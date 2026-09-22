package io.yak.ops.business.workflow.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditCarrier;
import io.yak.ops.business.audit.AuditContext;
import io.yak.ops.business.audit.AuditEventCategory;
import io.yak.ops.business.audit.AuditEventRequest;
import io.yak.ops.business.audit.AuditEventStatus;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.domain.WorkflowTriggerType;
import io.yak.ops.business.workflow.repository.WorkflowAuditCorrelationRepository;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Correlates one Workflow execution phase with a durable business AuditOperation. */
@Component
public class WorkflowExecutionAuditBridge {

  private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionAuditBridge.class);
  private static final String RESOURCE_TYPE = "WORKFLOW_EXECUTION";
  private static final BusinessAuditService NOOP_AUDIT =
      request -> AuditOperationHandle.noop(null);

  private final BusinessAuditService auditService;
  private final WorkflowAuditCorrelationRepository correlationRepository;
  private final ObjectMapper objectMapper;

  @Autowired
  public WorkflowExecutionAuditBridge(
      ObjectProvider<BusinessAuditService> auditServiceProvider,
      ObjectProvider<WorkflowAuditCorrelationRepository> correlationRepositoryProvider,
      ObjectMapper objectMapper) {
    this(
        auditServiceProvider.getIfAvailable(() -> NOOP_AUDIT),
        correlationRepositoryProvider.getIfAvailable(),
        objectMapper);
  }

  WorkflowExecutionAuditBridge(
      BusinessAuditService auditService,
      WorkflowAuditCorrelationRepository correlationRepository,
      ObjectMapper objectMapper) {
    this.auditService = auditService == null ? NOOP_AUDIT : auditService;
    this.correlationRepository = correlationRepository;
    this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
  }

  LaunchAudit beginLaunch(
      String launchMode,
      String target,
      WorkflowTriggerContext triggerContext) {
    String mode = required(launchMode, "launchMode must not be blank");
    WorkflowTriggerContext trigger = java.util.Objects.requireNonNull(triggerContext, "triggerContext");
    AuditOperationHandle handle =
        auditService.start(
            new AuditOperationRequest(
                "WORKFLOW_EXECUTE",
                operationName(mode),
                RESOURCE_TYPE,
                null,
                trimToNull(target),
                source(trigger.triggerType()),
                launchMetadata(mode, target, trigger)));
    return new LaunchAudit(handle, mode, trimToNull(target), trigger);
  }

  <T> T call(LaunchAudit audit, Supplier<T> action) {
    java.util.Objects.requireNonNull(action, "action");
    if (audit == null || audit.handle().carrier() == null) return action.get();
    return AuditContext.call(audit.handle().carrier(), action);
  }

  void attachLaunch(LaunchAudit audit, String executionId, String resourceName) {
    if (audit == null || !StringUtils.hasText(executionId)) return;
    String id = executionId.trim();
    String name = StringUtils.hasText(resourceName) ? resourceName.trim() : audit.target();
    audit.handle().resource(id, name);
    persistCarrier(id, audit.handle().carrier());
    emitState(
        audit.handle(),
        id,
        "started",
        AuditEventStatus.INFO,
        "EXECUTION_STARTED",
        "Workflow execution started",
        null,
        Map.of(
            "launchMode", audit.launchMode(),
            "triggerType", audit.triggerContext().triggerType().name()));
  }

  void failLaunch(LaunchAudit audit, RuntimeException exception) {
    if (audit != null) {
      audit.handle().failure("WORKFLOW_EXECUTION_START_FAILED", exception);
    }
  }

  /**
   * A retry/continue reuses the same durable WorkflowExecution but represents a new actor action.
   * Replace correlation before reactivation so a fast terminal transition belongs to the new actor.
   */
  <T> T reactivate(
      String executionId,
      String launchMode,
      String nodeId,
      Supplier<T> action) {
    String id = required(executionId, "executionId must not be blank");
    String mode = required(launchMode, "launchMode must not be blank");
    java.util.Objects.requireNonNull(action, "action");

    String previousCarrier = rawCarrier(id);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("launchMode", mode);
    putIfNotNull(metadata, "nodeId", trimToNull(nodeId));
    AuditOperationHandle handle =
        auditService.start(
            new AuditOperationRequest(
                "WORKFLOW_EXECUTE",
                operationName(mode),
                RESOURCE_TYPE,
                id,
                id,
                "WEB",
                Map.copyOf(metadata)));
    persistCarrier(id, handle.carrier());

    Map<String, Object> started = new LinkedHashMap<>();
    started.put("launchMode", mode);
    putIfNotNull(started, "nodeId", trimToNull(nodeId));
    emitState(
        handle,
        id,
        "started",
        AuditEventStatus.INFO,
        "EXECUTION_STARTED",
        "Workflow execution reactivated",
        null,
        Map.copyOf(started));

    try {
      AuditCarrier carrier = handle.carrier();
      return carrier == null ? action.get() : AuditContext.call(carrier, action);
    } catch (RuntimeException exception) {
      restoreCarrier(id, previousCarrier);
      handle.failure("WORKFLOW_EXECUTION_REACTIVATION_FAILED", exception);
      throw exception;
    }
  }

  /** AFTER_COMMIT projection from durable WorkflowExecution terminal truth. */
  public void observeTerminal(WorkflowExecutionTerminalEvent event) {
    if (event == null || !StringUtils.hasText(event.executionId())) return;
    try {
      String executionId = event.executionId().trim();
      AuditCarrier carrier = storedCarrier(executionId);
      if (carrier == null) carrier = AuditContext.current().orElse(null);
      if (carrier == null) return;

      AuditOperationHandle handle = auditService.resume(carrier);
      String status = normalizeStatus(event.executionStatus());
      switch (status) {
        case "SUCCESS" -> {
          terminalSuccess(handle, executionId, status, "Workflow execution succeeded");
        }
        case "SUCCESS_WITH_WARNINGS" -> {
          terminalSuccess(handle, executionId, status, "Workflow execution succeeded with warnings");
        }
        case "CANCELED" -> {
          terminalFailure(
              handle,
              executionId,
              status,
              "EXECUTION_CANCELED",
              "Workflow execution canceled",
              "WORKFLOW_EXECUTION_CANCELED");
        }
        case "TIMED_OUT" -> {
          terminalFailure(
              handle,
              executionId,
              status,
              "EXECUTION_FAILED",
              "Workflow execution timed out",
              "WORKFLOW_EXECUTION_TIMED_OUT");
        }
        case "WARNING" -> {
          terminalFailure(
              handle,
              executionId,
              status,
              "EXECUTION_FAILED",
              "Workflow execution finished with warning state",
              "WORKFLOW_EXECUTION_WARNING");
        }
        case "FAILED" -> {
          terminalFailure(
              handle,
              executionId,
              status,
              "EXECUTION_FAILED",
              "Workflow execution failed",
              "WORKFLOW_EXECUTION_FAILED");
        }
        default -> {
          // Only terminal vocabulary is projected; unknown future states remain fail-open.
        }
      }
    } catch (RuntimeException exception) {
      log.warn(
          "Workflow audit terminal projection failed; runtime result is unchanged, executionId={}",
          event.executionId(),
          exception);
    }
  }

  private void terminalSuccess(
      AuditOperationHandle handle,
      String executionId,
      String status,
      String summary) {
    emitState(
        handle,
        executionId,
        status.toLowerCase(Locale.ROOT),
        AuditEventStatus.SUCCESS,
        "EXECUTION_SUCCEEDED",
        summary,
        null,
        Map.of("status", status));
    handle.success(summary);
  }

  private void terminalFailure(
      AuditOperationHandle handle,
      String executionId,
      String status,
      String changeType,
      String message,
      String reasonCode) {
    emitState(
        handle,
        executionId,
        status.toLowerCase(Locale.ROOT),
        AuditEventStatus.FAILURE,
        changeType,
        message,
        reasonCode,
        Map.of("status", status));
    handle.failure(reasonCode, null);
  }

  private void emitState(
      AuditOperationHandle handle,
      String executionId,
      String suffix,
      AuditEventStatus status,
      String changeType,
      String message,
      String reasonCode,
      Map<String, ?> additionalPayload) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("changeType", changeType);
    payload.put("executionId", executionId);
    if (additionalPayload != null) payload.putAll(additionalPayload);
    handle.event(
        new AuditEventRequest(
            AuditEventType.RESOURCE_UPDATED,
            AuditEventCategory.BUSINESS,
            status,
            eventKey(executionId, suffix),
            RESOURCE_TYPE,
            executionId,
            message,
            reasonCode,
            Map.copyOf(payload)));
  }

  private void persistCarrier(String executionId, AuditCarrier carrier) {
    if (carrier == null || correlationRepository == null) return;
    try {
      String value = objectMapper.writeValueAsString(carrier);
      if (!correlationRepository.replaceCarrierJson(executionId, value)) {
        log.warn("Unable to persist Workflow audit carrier, executionId={}", executionId);
      }
    } catch (JsonProcessingException | RuntimeException exception) {
      log.warn(
          "Workflow audit carrier persistence failed; execution will continue, executionId={}",
          executionId,
          exception);
    }
  }

  private void restoreCarrier(String executionId, String previousCarrier) {
    if (correlationRepository == null) return;
    try {
      if (!correlationRepository.replaceCarrierJson(executionId, previousCarrier)) {
        log.warn("Unable to restore Workflow audit carrier, executionId={}", executionId);
      }
    } catch (RuntimeException exception) {
      log.warn(
          "Workflow audit carrier restore failed; business result is unchanged, executionId={}",
          executionId,
          exception);
    }
  }

  private String rawCarrier(String executionId) {
    if (correlationRepository == null) return null;
    try {
      return correlationRepository.findCarrierJson(executionId).orElse(null);
    } catch (RuntimeException exception) {
      log.warn("Unable to read Workflow audit carrier, executionId={}", executionId, exception);
      return null;
    }
  }

  private AuditCarrier storedCarrier(String executionId) {
    String value = rawCarrier(executionId);
    if (!StringUtils.hasText(value)) return null;
    try {
      return objectMapper.readValue(value, AuditCarrier.class);
    } catch (JsonProcessingException exception) {
      log.warn("Workflow audit carrier is invalid, executionId={}", executionId, exception);
      return null;
    }
  }

  private Map<String, Object> launchMetadata(
      String launchMode,
      String target,
      WorkflowTriggerContext trigger) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("launchMode", launchMode);
    metadata.put("triggerType", trigger.triggerType().name());
    putIfNotNull(metadata, "target", trimToNull(target));
    putIfNotNull(metadata, "triggerId", trimToNull(trigger.triggerId()));
    putIfNotNull(metadata, "scheduleId", trimToNull(trigger.scheduleId()));
    putIfNotNull(metadata, "backfillId", trimToNull(trigger.backfillId()));
    if (trigger.plannedFireTime() != null) {
      metadata.put("plannedFireTime", trigger.plannedFireTime().toString());
    }
    putIfNotNull(metadata, "timezone", trimToNull(trigger.timezone()));
    return Map.copyOf(metadata);
  }

  private String source(WorkflowTriggerType triggerType) {
    return switch (triggerType) {
      case MANUAL, RERUN -> "WEB";
      case API -> "API";
      case SCHEDULE -> "SCHEDULE";
      case BACKFILL -> "BACKFILL";
    };
  }

  private String operationName(String launchMode) {
    return switch (launchMode) {
      case "DRAFT_TEST" -> "Test workflow";
      case "RESTART" -> "Restart workflow execution";
      case "RERUN_FROM_NODE" -> "Rerun workflow from node";
      case "CONTINUE_AFTER_FAILURE" -> "Continue workflow after failure";
      case "RETRY_FAILED_NODE" -> "Retry failed workflow node";
      case "RETRY_FAILED_NODES" -> "Retry failed workflow nodes";
      default -> "Execute workflow";
    };
  }

  private String eventKey(String executionId, String suffix) {
    return "workflow:execution:" + executionId + ":" + suffix;
  }

  private String normalizeStatus(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String required(String value, String message) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private void putIfNotNull(Map<String, Object> target, String key, Object value) {
    if (value != null) target.put(key, value);
  }

  record LaunchAudit(
      AuditOperationHandle handle,
      String launchMode,
      String target,
      WorkflowTriggerContext triggerContext) {}
}
