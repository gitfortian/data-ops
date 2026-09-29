package io.yak.ops.business.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC-backed, fail-open implementation of the shared business audit contract. */
final class JdbcBusinessAuditService implements BusinessAuditService {

  private static final Logger log = LoggerFactory.getLogger(JdbcBusinessAuditService.class);
  private static final int SCHEMA_VERSION = 1;
  private static final int MAX_EVENT_KEY_LENGTH = 160;

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final CurrentProject currentProject;
  private final ObjectMapper objectMapper;
  private final AuditActorResolver actorResolver;

  JdbcBusinessAuditService(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      CurrentProject currentProject,
      ObjectMapper objectMapper) {
    this(
        dataSource,
        transactionManager,
        currentProject,
        objectMapper,
        new SpringSecurityAuditActorResolver());
  }

  JdbcBusinessAuditService(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      CurrentProject currentProject,
      ObjectMapper objectMapper,
      AuditActorResolver actorResolver) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.currentProject = currentProject;
    this.objectMapper = objectMapper;
    this.actorResolver = actorResolver;
  }

  @Override
  public AuditOperationHandle start(AuditOperationRequest request) {
    return startInternal(request, true);
  }

  @Override
  public AuditOperationHandle resume(AuditCarrier carrier) {
    if (carrier == null) return AuditOperationHandle.noop(null);
    AuditCarrier persisted = loadCarrier(carrier.operationId());
    return persisted == null
        ? AuditOperationHandle.noop(null)
        : new JdbcAuditOperationHandle(persisted, true);
  }

  @Override
  public void authorizationDecision(AuditAuthorizationDecision decision) {
    if (decision == null) return;

    AuditCarrier activeCarrier = AuditContext.current().orElse(null);
    if (activeCarrier != null) {
      resume(activeCarrier).event(authorizationEvent(decision));
      return;
    }

    if (decision.allowed()) {
      AuditAuthorizationDecisionContext.defer(decision);
      return;
    }

    writeStandaloneAuthorizationDecision(decision);
  }

  @Override
  public void clearAuthorizationDecisions() {
    AuditAuthorizationDecisionContext.clear();
  }

  private AuditOperationHandle startInternal(
      AuditOperationRequest request, boolean attachDeferredAuthorization) {
    AuditWebLedger.markSdkOperationOpened();
    List<AuditAuthorizationDecision> deferredAuthorization =
        attachDeferredAuthorization ? AuditAuthorizationDecisionContext.drain() : List.of();
    String operationId = "AUD-" + UUID.randomUUID();
    AuditActor actor = safeActor();
    ProjectContext project = currentProject.current().orElse(null);
    AuditCarrier carrier =
        new AuditCarrier(
            operationId,
            actor.id(),
            actor.name(),
            actor.type(),
            project == null ? null : project.projectId(),
            project == null ? null : project.projectName(),
            request.resourceType(),
            request.resourceId(),
            request.resourceName(),
            request.source());
    String traceId = MDC.get("traceId");
    LocalDateTime now = LocalDateTime.now();
    boolean persisted =
        safeWrite(
            () -> {
              jdbcTemplate.update(
                  """
                  INSERT INTO yak_audit_operation (
                    operation_id, operation_type, operation_name, actor_id, actor_name,
                    project_id, project_name, resource_type, resource_id, resource_name,
                    status, source, started_at, root_trace_id, metadata_json, schema_version,
                    created_at, updated_at
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?)
                  """,
                  operationId,
                  request.operationType(),
                  request.operationName(),
                  carrier.actorId(),
                  carrier.actorName(),
                  carrier.projectId(),
                  carrier.projectName(),
                  carrier.resourceType(),
                  carrier.resourceId(),
                  carrier.resourceName(),
                  carrier.source(),
                  now,
                  traceId,
                  json(request.metadata()),
                  SCHEMA_VERSION,
                  now,
                  now);
              for (AuditAuthorizationDecision decision : deferredAuthorization) {
                insertAuthorizationEvent(carrier, decision);
              }
              insertEvent(
                  carrier,
                  AuditEventType.OPERATION_STARTED,
                  AuditEventCategory.BUSINESS,
                  AuditEventStatus.INFO,
                  "operation:started",
                  null,
                  null,
                  "Operation started",
                  null,
                  Map.of());
            });
    return new JdbcAuditOperationHandle(carrier, persisted);
  }

  private void writeStandaloneAuthorizationDecision(AuditAuthorizationDecision decision) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("permission", decision.permission());
    metadata.put("decision", decision.decision().name());
    AuditOperationHandle handle =
        startInternal(
            new AuditOperationRequest(
                "AUTHORIZATION_CHECK",
                "Authorize " + decision.permission(),
                decision.resourceType(),
                decision.resourceId(),
                decision.resourceName(),
                "WEB",
                metadata),
            false);
    handle.event(authorizationEvent(decision));
    if (decision.allowed()) {
      handle.success("Authorization allowed");
    } else {
      handle.failure(decision.reasonCode(), null);
    }
  }

  private void insertAuthorizationEvent(
      AuditCarrier carrier, AuditAuthorizationDecision decision) {
    AuditEventRequest request = authorizationEvent(decision);
    insertEvent(
        carrier,
        request.type(),
        request.category(),
        request.status(),
        request.eventKey(),
        request.resourceType(),
        request.resourceId(),
        request.message(),
        request.reasonCode(),
        request.payload());
  }

  private AuditEventRequest authorizationEvent(AuditAuthorizationDecision decision) {
    return AuditEventRequest.authorization(decision, authorizationEventKey(decision));
  }

  private String authorizationEventKey(AuditAuthorizationDecision decision) {
    String key =
        "authorization:"
            + keyToken(decision.permission())
            + ":"
            + keyToken(decision.resourceType())
            + ":"
            + keyToken(decision.resourceId())
            + ":"
            + decision.decision().name().toLowerCase(Locale.ROOT)
            + ":"
            + keyToken(decision.reasonCode());
    return key.length() <= MAX_EVENT_KEY_LENGTH ? key : key.substring(0, MAX_EVENT_KEY_LENGTH);
  }

  private String keyToken(String value) {
    if (value == null || value.isBlank()) return "none";
    return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
  }

  private final class JdbcAuditOperationHandle implements AuditOperationHandle {
    private final AuditCarrier initialCarrier;
    private final boolean persisted;
    private volatile String resourceId;
    private volatile String resourceName;

    private JdbcAuditOperationHandle(AuditCarrier carrier, boolean persisted) {
      this.initialCarrier = carrier;
      this.resourceId = carrier.resourceId();
      this.resourceName = carrier.resourceName();
      this.persisted = persisted;
    }

    @Override
    public String operationId() {
      return initialCarrier.operationId();
    }

    @Override
    public AuditCarrier carrier() {
      return initialCarrier.withResource(resourceId, resourceName);
    }

    @Override
    public void resource(String resourceId, String resourceName) {
      this.resourceId = resourceId;
      this.resourceName = resourceName;
      if (!persisted) return;
      safeWrite(
          () ->
              jdbcTemplate.update(
                  "UPDATE yak_audit_operation SET resource_id = ?, resource_name = ?, updated_at = ? WHERE operation_id = ?",
                  resourceId,
                  resourceName,
                  LocalDateTime.now(),
                  operationId()));
    }

    @Override
    public void event(AuditEventType type, String message, Map<String, ?> payload) {
      event(new AuditEventRequest(type, null, message, null, payload));
    }

    @Override
    public void event(AuditEventRequest request) {
      if (!persisted || request == null) return;
      AuditEventCategory category =
          request.category() == null ? AuditEventCategory.BUSINESS : request.category();
      AuditEventStatus status =
          request.status() == null ? eventStatus(request.type()) : request.status();
      safeWrite(
          () ->
              insertEvent(
                  carrier(),
                  request.type(),
                  category,
                  status,
                  request.eventKey(),
                  request.resourceType(),
                  request.resourceId(),
                  request.message(),
                  request.reasonCode(),
                  request.payload()));
    }

    @Override
    public void success(String summary) {
      if (!persisted) return;
      safeWrite(
          () -> {
            LocalDateTime now = LocalDateTime.now();
            int updated =
                jdbcTemplate.update(
                    """
                    UPDATE yak_audit_operation
                       SET status = 'SUCCEEDED', summary = ?, finished_at = ?, updated_at = ?
                     WHERE operation_id = ? AND status = 'RUNNING'
                    """,
                    summary,
                    now,
                    now,
                    operationId());
            if (updated > 0) {
              insertEvent(
                  carrier(),
                  AuditEventType.OPERATION_SUCCEEDED,
                  AuditEventCategory.BUSINESS,
                  AuditEventStatus.SUCCESS,
                  "operation:succeeded",
                  null,
                  null,
                  summary == null ? "Operation succeeded" : summary,
                  null,
                  Map.of());
            }
          });
    }

    @Override
    public void failure(String reasonCode, Throwable cause) {
      if (!persisted) return;
      safeWrite(
          () -> {
            LocalDateTime now = LocalDateTime.now();
            String summary =
                reasonCode == null || reasonCode.isBlank() ? "OPERATION_FAILED" : reasonCode;
            int updated =
                jdbcTemplate.update(
                    """
                    UPDATE yak_audit_operation
                       SET status = 'FAILED', error_code = ?, summary = ?, finished_at = ?, updated_at = ?
                     WHERE operation_id = ? AND status = 'RUNNING'
                    """,
                    reasonCode,
                    summary,
                    now,
                    now,
                    operationId());
            if (updated > 0) {
              insertEvent(
                  carrier(),
                  AuditEventType.OPERATION_FAILED,
                  AuditEventCategory.BUSINESS,
                  AuditEventStatus.FAILURE,
                  "operation:failed",
                  null,
                  null,
                  summary,
                  reasonCode,
                  cause == null ? Map.of() : Map.of("exceptionType", cause.getClass().getName()));
            }
          });
    }
  }

  private void insertEvent(
      AuditCarrier carrier,
      AuditEventType type,
      AuditEventCategory category,
      AuditEventStatus eventStatus,
      String eventKey,
      String eventResourceType,
      String eventResourceId,
      String message,
      String reasonCode,
      Map<String, ?> payload) {
    LocalDateTime now = LocalDateTime.now();
    String sql =
        """
        INSERT INTO yak_audit_event (
          operation_id, event_type, event_category, event_status, occurred_at,
          actor_id, resource_type, resource_id, trace_id, span_id,
          reason_code, event_key, message, payload_json, schema_version, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    if (eventKey != null) {
      sql += " ON DUPLICATE KEY UPDATE id = id";
    }
    jdbcTemplate.update(
        sql,
        carrier.operationId(),
        type.name(),
        category.name(),
        eventStatus.name(),
        now,
        carrier.actorId(),
        eventResourceType == null ? carrier.resourceType() : eventResourceType,
        eventResourceId == null ? carrier.resourceId() : eventResourceId,
        MDC.get("traceId"),
        MDC.get("spanId"),
        reasonCode,
        eventKey,
        message,
        json(payload),
        SCHEMA_VERSION,
        now);
  }

  private AuditCarrier loadCarrier(String operationId) {
    try {
      return transactionTemplate.execute(
          status ->
              jdbcTemplate.query(
                  """
                  SELECT actor_id, actor_name, project_id, project_name,
                         resource_type, resource_id, resource_name, source
                    FROM yak_audit_operation
                   WHERE operation_id = ?
                  """,
                  resultSet -> {
                    if (!resultSet.next()) return null;
                    Number projectId = (Number) resultSet.getObject("project_id");
                    String actorId = resultSet.getString("actor_id");
                    String actorName = resultSet.getString("actor_name");
                    return new AuditCarrier(
                        operationId,
                        actorId,
                        actorName,
                        actorId == null && actorName == null ? "SYSTEM" : "USER",
                        projectId == null ? null : projectId.longValue(),
                        resultSet.getString("project_name"),
                        resultSet.getString("resource_type"),
                        resultSet.getString("resource_id"),
                        resultSet.getString("resource_name"),
                        resultSet.getString("source"));
                  },
                  operationId));
    } catch (RuntimeException exception) {
      log.warn("Business audit resume failed; the business action will continue", exception);
      return null;
    }
  }

  private boolean safeWrite(Runnable write) {
    try {
      transactionTemplate.executeWithoutResult(status -> write.run());
      return true;
    } catch (RuntimeException exception) {
      log.warn("Business audit write failed; the business action will continue", exception);
      return false;
    }
  }

  private AuditActor safeActor() {
    try {
      AuditActor actor = actorResolver == null ? null : actorResolver.currentActor();
      return actor == null ? AuditActor.system() : actor;
    } catch (RuntimeException exception) {
      log.warn("Unable to resolve audit actor; recording a system actor snapshot", exception);
      return AuditActor.system();
    }
  }

  private String json(Map<String, ?> value) {
    if (value == null || value.isEmpty()) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      log.debug("Unable to serialize audit payload", exception);
      return null;
    }
  }

  private static AuditEventStatus eventStatus(AuditEventType type) {
    return switch (type) {
      case RESOURCE_CREATED,
          RESOURCE_UPDATED,
          RESOURCE_DELETED,
          TASK_SUCCEEDED,
          OPERATION_SUCCEEDED -> AuditEventStatus.SUCCESS;
      case TASK_FAILED, TASK_CANCELED, OPERATION_FAILED -> AuditEventStatus.FAILURE;
      case OPERATION_STARTED,
          AUTHORIZATION_DECISION,
          TASK_SUBMITTED,
          TASK_QUEUED,
          WORKER_STARTED -> AuditEventStatus.INFO;
    };
  }
}
