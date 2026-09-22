package io.yak.ops.business.workflow.backfill;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.workflow.execution.WorkflowBusinessDateRerunGateway;
import io.yak.ops.common.bean.dto.workflow.WorkflowBackfillCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowBusinessDateRerunDTO;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Audits Backfill batch management while child WorkflowExecution audit remains owned by Launcher. */
@Component
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowBackfillAuditCoordinator implements WorkflowBusinessDateRerunGateway {

  private static final String RESOURCE_TYPE = "WORKFLOW_BACKFILL";
  private static final BusinessAuditService NOOP_AUDIT =
      request -> AuditOperationHandle.noop(null);

  private final WorkflowBackfillManager manager;
  private final WorkflowBackfillQuery query;
  private final BusinessAuditService auditService;

  @Autowired
  public WorkflowBackfillAuditCoordinator(
      WorkflowBackfillManager manager,
      WorkflowBackfillQuery query,
      ObjectProvider<BusinessAuditService> auditServiceProvider) {
    this(manager, query, auditServiceProvider.getIfAvailable(() -> NOOP_AUDIT));
  }

  WorkflowBackfillAuditCoordinator(
      WorkflowBackfillManager manager,
      WorkflowBackfillQuery query,
      BusinessAuditService auditService) {
    this.manager = manager;
    this.query = query;
    this.auditService = auditService == null ? NOOP_AUDIT : auditService;
  }

  public WorkflowBackfillVO create(WorkflowBackfillCreateDTO request) {
    AuditOperationHandle audit =
        start(
            "WORKFLOW_BACKFILL_CREATE",
            "Create workflow Backfill",
            null,
            request == null ? null : trimToNull(request.name()),
            createMetadata(request));
    try {
      WorkflowBackfillVO created = manager.create(request);
      audit.resource(created.id(), created.name());
      audit.event(
          AuditEventType.RESOURCE_CREATED,
          "Workflow Backfill created",
          snapshot("BACKFILL_CREATED", created));
      audit.success("Workflow Backfill created");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_BACKFILL_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Override
  public WorkflowBackfillVO createBusinessDateRerun(
      String sourceExecutionId,
      WorkflowInstanceVO source,
      WorkflowBusinessDateRerunDTO request) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfNotNull(metadata, "sourceExecutionId", trimToNull(sourceExecutionId));
    if (request != null) {
      putIfNotNull(metadata, "businessDate", request.businessDate());
      putIfNotNull(metadata, "executionStrategy", trimToNull(request.executionStrategy()));
      metadata.put("inputConfigured", request.input() != null && !request.input().isEmpty());
    }
    AuditOperationHandle audit =
        start(
            "WORKFLOW_BUSINESS_DATE_RERUN",
            "Create workflow business-date rerun",
            null,
            null,
            Map.copyOf(metadata));
    try {
      WorkflowBackfillVO created =
          manager.createBusinessDateRerun(sourceExecutionId, source, request);
      audit.resource(created.id(), created.name());
      audit.event(
          AuditEventType.RESOURCE_CREATED,
          "Workflow business-date rerun created",
          snapshot("BUSINESS_DATE_RERUN_CREATED", created));
      audit.success("Workflow business-date rerun created");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_BUSINESS_DATE_RERUN_FAILED", exception);
      throw exception;
    }
  }

  public WorkflowBackfillVO cancel(String id) {
    WorkflowBackfillPO durable = query.require(id);
    if ("CANCELED".equals(durable.getStatus())) {
      return manager.cancel(id);
    }
    WorkflowBackfillVO before = query.view(durable);

    AuditOperationHandle audit =
        start(
            "WORKFLOW_BACKFILL_CANCEL",
            "Cancel workflow Backfill",
            before.id(),
            before.name(),
            operationMetadata(before));
    try {
      WorkflowBackfillVO canceled = manager.cancel(id);
      audit.resource(canceled.id(), canceled.name());
      Map<String, Object> payload = new LinkedHashMap<>(snapshot("BACKFILL_CANCELED", canceled));
      payload.put("waitingBefore", before.waitingCount());
      payload.put("runningBefore", before.runningCount());
      audit.event(
          AuditEventType.RESOURCE_UPDATED,
          "Workflow Backfill canceled",
          Map.copyOf(payload));
      audit.success("Workflow Backfill canceled");
      return canceled;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_BACKFILL_CANCEL_FAILED", exception);
      throw exception;
    }
  }

  private AuditOperationHandle start(
      String operationType,
      String operationName,
      String resourceId,
      String resourceName,
      Map<String, ?> metadata) {
    return auditService.start(
        new AuditOperationRequest(
            operationType,
            operationName,
            RESOURCE_TYPE,
            resourceId,
            resourceName,
            "WEB",
            metadata));
  }

  private Map<String, Object> createMetadata(WorkflowBackfillCreateDTO request) {
    if (request == null) return Map.of();
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfNotNull(metadata, "scheduleId", trimToNull(request.scheduleId()));
    putIfNotNull(metadata, "startBusinessDate", request.startBusinessDate());
    putIfNotNull(metadata, "endBusinessDate", request.endBusinessDate());
    putIfNotNull(metadata, "executionStrategy", trimToNull(request.executionStrategy()));
    metadata.put("inputConfigured", request.input() != null && !request.input().isEmpty());
    return Map.copyOf(metadata);
  }

  private Map<String, Object> operationMetadata(WorkflowBackfillVO backfill) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfNotNull(metadata, "workflowId", backfill.workflowId());
    putIfNotNull(metadata, "scheduleId", backfill.scheduleId());
    putIfNotNull(metadata, "operationType", backfill.operationType());
    putIfNotNull(metadata, "previousStatus", backfill.status());
    return Map.copyOf(metadata);
  }

  private Map<String, Object> snapshot(String changeType, WorkflowBackfillVO backfill) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("changeType", changeType);
    putIfNotNull(payload, "workflowId", backfill.workflowId());
    putIfNotNull(payload, "workflowVersionId", backfill.workflowVersionId());
    putIfNotNull(payload, "workflowVersionNo", backfill.workflowVersionNo());
    putIfNotNull(payload, "scheduleId", backfill.scheduleId());
    putIfNotNull(payload, "operationType", backfill.operationType());
    putIfNotNull(payload, "sourceExecutionId", backfill.sourceExecutionId());
    putIfNotNull(payload, "startBusinessDate", backfill.startBusinessDate());
    putIfNotNull(payload, "endBusinessDate", backfill.endBusinessDate());
    putIfNotNull(payload, "timezone", backfill.timezone());
    putIfNotNull(payload, "executionStrategy", backfill.executionStrategy());
    putIfNotNull(payload, "status", backfill.status());
    payload.put("totalCount", backfill.totalCount());
    payload.put("waitingCount", backfill.waitingCount());
    payload.put("runningCount", backfill.runningCount());
    payload.put("succeededCount", backfill.succeededCount());
    payload.put("failedCount", backfill.failedCount());
    payload.put("canceledCount", backfill.canceledCount());
    payload.put("skippedCount", backfill.skippedCount());
    payload.put("inputConfigured", backfill.input() != null && !backfill.input().isEmpty());
    return Map.copyOf(payload);
  }

  private static void putIfNotNull(Map<String, Object> values, String key, Object value) {
    if (value != null) values.put(key, value);
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
