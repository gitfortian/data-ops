package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleUpdateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Adds business-audit semantics around Workflow Schedule management without owning schedule truth. */
@Component
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowScheduleAuditCoordinator {

  private static final String RESOURCE_TYPE = "WORKFLOW_SCHEDULE";
  private static final BusinessAuditService NOOP_AUDIT =
      request -> AuditOperationHandle.noop(null);

  private final WorkflowScheduleCreateCommand creator;
  private final WorkflowScheduleRevision revision;
  private final WorkflowScheduleLifecycle lifecycle;
  private final WorkflowScheduleQuery query;
  private final BusinessAuditService auditService;

  @Autowired
  public WorkflowScheduleAuditCoordinator(
      WorkflowScheduleCreateCommand creator,
      WorkflowScheduleRevision revision,
      WorkflowScheduleLifecycle lifecycle,
      WorkflowScheduleQuery query,
      ObjectProvider<BusinessAuditService> auditServiceProvider) {
    this(
        creator,
        revision,
        lifecycle,
        query,
        auditServiceProvider.getIfAvailable(() -> NOOP_AUDIT));
  }

  WorkflowScheduleAuditCoordinator(
      WorkflowScheduleCreateCommand creator,
      WorkflowScheduleRevision revision,
      WorkflowScheduleLifecycle lifecycle,
      WorkflowScheduleQuery query,
      BusinessAuditService auditService) {
    this.creator = creator;
    this.revision = revision;
    this.lifecycle = lifecycle;
    this.query = query;
    this.auditService = auditService == null ? NOOP_AUDIT : auditService;
  }

  public WorkflowScheduleVO create(WorkflowScheduleCreateDTO request) {
    AuditOperationHandle audit =
        start(
            "WORKFLOW_SCHEDULE_CREATE",
            "Create workflow schedule",
            null,
            request == null ? null : trimToNull(request.name()),
            "WEB",
            createMetadata(request));
    try {
      WorkflowScheduleVO created = creator.create(request);
      audit.resource(created.id(), created.name());
      audit.event(
          AuditEventType.RESOURCE_CREATED,
          "Workflow schedule created",
          scheduleSnapshot("SCHEDULE_CREATED", created, null));
      audit.success("Workflow schedule created");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_SCHEDULE_CREATE_FAILED", exception);
      throw exception;
    }
  }

  public WorkflowScheduleVO update(String id, WorkflowScheduleUpdateDTO request) {
    WorkflowScheduleVO before = query.get(id);
    UpdateChanges changes = updateChanges(before, request);
    if (!changes.changed()) {
      return revision.save(id, request);
    }

    AuditOperationHandle audit =
        start(
            "WORKFLOW_SCHEDULE_UPDATE",
            "Update workflow schedule",
            before.id(),
            before.name(),
            "WEB",
            scheduleMetadata(before, "MANUAL"));
    try {
      WorkflowScheduleVO updated = revision.save(id, request);
      audit.resource(updated.id(), updated.name());
      Map<String, Object> payload = new LinkedHashMap<>(changes.payload());
      payload.put("changeType", "SCHEDULE_UPDATED");
      audit.event(
          AuditEventType.RESOURCE_UPDATED,
          "Workflow schedule updated",
          Map.copyOf(payload));
      audit.success("Workflow schedule updated");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_SCHEDULE_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  public WorkflowScheduleVO online(String id) {
    return transition(id, true, "WEB", "MANUAL");
  }

  public WorkflowScheduleVO offline(String id) {
    return transition(id, false, "WEB", "MANUAL");
  }

  /**
   * Parent WORKFLOW_ENABLE already owns the user's business intent and authorization decision.
   * Do not create a second Schedule AuditOperation for this derived side effect.
   */
  public WorkflowScheduleVO onlineFromWorkflow(String id, String workflowId) {
    return lifecycle.online(id);
  }

  /**
   * Parent WORKFLOW_DISABLE is intentionally started after schedules are safely paused. Keeping
   * this side effect audit-silent also prevents it from claiming the deferred authorization event.
   */
  public WorkflowScheduleVO offlineFromWorkflow(String id, String workflowId) {
    return lifecycle.offline(id);
  }

  /** Scheduler-side safety action when the owning Workflow can no longer produce executions. */
  public WorkflowScheduleVO offlineFromScheduler(String id, String cause) {
    return transition(id, false, "SCHEDULE", normalizeCause(cause, "SCHEDULER_AUTO_DISABLE"));
  }

  public WorkflowScheduleVO expire(String id, Instant fireTime) {
    WorkflowScheduleVO before = query.get(id);
    AuditOperationHandle audit =
        start(
            "WORKFLOW_SCHEDULE_EXPIRE",
            "Expire workflow schedule",
            before.id(),
            before.name(),
            "SCHEDULE",
            scheduleMetadata(before, "END_TIME_EXPIRED"));
    try {
      WorkflowScheduleVO expired = lifecycle.expire(id, fireTime);
      audit.resource(expired.id(), expired.name());
      Map<String, Object> payload =
          new LinkedHashMap<>(scheduleSnapshot("SCHEDULE_EXPIRED", expired, "END_TIME_EXPIRED"));
      putIfNotNull(payload, "fireTime", fireTime);
      audit.event(
          AuditEventType.RESOURCE_UPDATED,
          "Workflow schedule expired",
          Map.copyOf(payload));
      audit.success("Workflow schedule expired");
      return expired;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_SCHEDULE_EXPIRE_FAILED", exception);
      throw exception;
    }
  }

  public void remove(String id) {
    WorkflowScheduleVO before = query.get(id);
    AuditOperationHandle audit =
        start(
            "WORKFLOW_SCHEDULE_DELETE",
            "Delete workflow schedule",
            before.id(),
            before.name(),
            "WEB",
            scheduleMetadata(before, "MANUAL"));
    try {
      lifecycle.remove(id);
      audit.event(
          AuditEventType.RESOURCE_DELETED,
          "Workflow schedule deleted",
          scheduleSnapshot("SCHEDULE_DELETED", before, "MANUAL"));
      audit.success("Workflow schedule deleted");
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_SCHEDULE_DELETE_FAILED", exception);
      throw exception;
    }
  }

  private WorkflowScheduleVO transition(
      String id, boolean online, String source, String cause) {
    WorkflowScheduleVO before = query.get(id);
    String targetStatus = online ? "ONLINE" : "OFFLINE";
    if (targetStatus.equals(before.status())) {
      return online ? lifecycle.online(id) : lifecycle.offline(id);
    }

    String operationType = online ? "WORKFLOW_SCHEDULE_ENABLE" : "WORKFLOW_SCHEDULE_DISABLE";
    String operationName = online ? "Enable workflow schedule" : "Disable workflow schedule";
    String reasonCode = online
        ? "WORKFLOW_SCHEDULE_ENABLE_FAILED"
        : "WORKFLOW_SCHEDULE_DISABLE_FAILED";
    String changeType = online ? "SCHEDULE_ENABLED" : "SCHEDULE_DISABLED";
    String message = online ? "Workflow schedule enabled" : "Workflow schedule disabled";
    AuditOperationHandle audit =
        start(
            operationType,
            operationName,
            before.id(),
            before.name(),
            source,
            scheduleMetadata(before, cause));
    try {
      WorkflowScheduleVO updated = online ? lifecycle.online(id) : lifecycle.offline(id);
      audit.resource(updated.id(), updated.name());
      audit.event(
          AuditEventType.RESOURCE_UPDATED,
          message,
          scheduleSnapshot(changeType, updated, cause));
      audit.success(message);
      return updated;
    } catch (RuntimeException exception) {
      audit.failure(reasonCode, exception);
      throw exception;
    }
  }

  private AuditOperationHandle start(
      String operationType,
      String operationName,
      String resourceId,
      String resourceName,
      String source,
      Map<String, ?> metadata) {
    return auditService.start(
        new AuditOperationRequest(
            operationType,
            operationName,
            RESOURCE_TYPE,
            resourceId,
            resourceName,
            source,
            metadata));
  }

  private Map<String, Object> createMetadata(WorkflowScheduleCreateDTO request) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (request == null) return Map.of();
    putIfNotNull(metadata, "workflowId", trimToNull(request.workflowId()));
    putIfNotNull(metadata, "cronExpression", trimToNull(request.cronExpression()));
    putIfNotNull(metadata, "timezone", trimToNull(request.timezone()));
    putIfNotNull(metadata, "executionStrategy", trimToNull(request.executionStrategy()));
    putIfNotNull(metadata, "misfireStrategy", trimToNull(request.misfireStrategy()));
    metadata.put("inputConfigured", request.input() != null && !request.input().isEmpty());
    return Map.copyOf(metadata);
  }

  private Map<String, Object> scheduleMetadata(WorkflowScheduleVO schedule, String cause) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfNotNull(metadata, "workflowId", schedule.workflowId());
    putIfNotNull(metadata, "previousStatus", schedule.status());
    putIfNotNull(metadata, "cause", trimToNull(cause));
    return Map.copyOf(metadata);
  }

  private Map<String, Object> scheduleSnapshot(
      String changeType, WorkflowScheduleVO schedule, String cause) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("changeType", changeType);
    putIfNotNull(payload, "workflowId", schedule.workflowId());
    putIfNotNull(payload, "triggerType", schedule.triggerType());
    putIfNotNull(payload, "cronExpression", schedule.cronExpression());
    putIfNotNull(payload, "timezone", schedule.timezone());
    putIfNotNull(payload, "status", schedule.status());
    putIfNotNull(payload, "executionStrategy", schedule.executionStrategy());
    putIfNotNull(payload, "misfireStrategy", schedule.misfireStrategy());
    putIfNotNull(payload, "startTime", schedule.startTime());
    putIfNotNull(payload, "endTime", schedule.endTime());
    putIfNotNull(payload, "cause", trimToNull(cause));
    payload.put("inputConfigured", schedule.input() != null && !schedule.input().isEmpty());
    return Map.copyOf(payload);
  }

  private UpdateChanges updateChanges(
      WorkflowScheduleVO before, WorkflowScheduleUpdateDTO request) {
    if (request == null) return new UpdateChanges(true, Map.of());
    String name = trimToNull(request.name());
    String cron = trimToNull(request.cronExpression());
    String timezone = trimToNull(request.timezone());
    String executionStrategy = trimToNull(request.executionStrategy());
    String misfireStrategy = trimToNull(request.misfireStrategy());

    Map<String, Object> payload = new LinkedHashMap<>();
    addValueChange(payload, "name", before.name(), name);
    addValueChange(payload, "cronExpression", before.cronExpression(), cron);
    addValueChange(payload, "timezone", before.timezone(), timezone);
    addValueChange(payload, "startTime", before.startTime(), request.startTime());
    addValueChange(payload, "endTime", before.endTime(), request.endTime());
    addValueChange(payload, "executionStrategy", before.executionStrategy(), executionStrategy);
    addValueChange(payload, "misfireStrategy", before.misfireStrategy(), misfireStrategy);
    if (!Objects.equals(before.input(), request.input())) {
      payload.put("inputChanged", true);
    }
    return new UpdateChanges(!payload.isEmpty(), Map.copyOf(payload));
  }

  private static void addValueChange(
      Map<String, Object> payload, String field, Object before, Object after) {
    if (Objects.equals(before, after)) return;
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("before", before);
    change.put("after", after);
    payload.put(field, Collections.unmodifiableMap(change));
  }

  private static void putIfNotNull(Map<String, Object> values, String key, Object value) {
    if (value != null) values.put(key, value);
  }

  private static String normalizeCause(String value, String fallback) {
    String normalized = trimToNull(value);
    return normalized == null ? fallback : normalized;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private record UpdateChanges(boolean changed, Map<String, Object> payload) {}
}
