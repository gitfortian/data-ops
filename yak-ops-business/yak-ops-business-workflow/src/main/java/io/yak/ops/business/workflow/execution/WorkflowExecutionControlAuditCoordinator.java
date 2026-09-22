package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.audit.AuditEventCategory;
import io.yak.ops.business.audit.AuditEventRequest;
import io.yak.ops.business.audit.AuditEventStatus;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Audits short actor-owned pause/resume/cancel commands without replacing execution correlation. */
@Component
public class WorkflowExecutionControlAuditCoordinator {

  private static final BusinessAuditService NOOP_AUDIT =
      request -> AuditOperationHandle.noop(null);

  private final WorkflowRuntime runtime;
  private final BusinessAuditService auditService;

  @Autowired
  public WorkflowExecutionControlAuditCoordinator(
      WorkflowRuntime runtime,
      ObjectProvider<BusinessAuditService> auditServiceProvider) {
    this(runtime, auditServiceProvider.getIfAvailable(() -> NOOP_AUDIT));
  }

  WorkflowExecutionControlAuditCoordinator(
      WorkflowRuntime runtime,
      BusinessAuditService auditService) {
    this.runtime = runtime;
    this.auditService = auditService == null ? NOOP_AUDIT : auditService;
  }

  public WorkflowInstanceVO pause(String executionId) {
    String id = required(executionId);
    return control(
        "WORKFLOW_EXECUTION_PAUSE",
        "Pause workflow execution",
        "EXECUTION_PAUSED",
        "Workflow execution paused",
        id,
        () -> runtime.pause(id));
  }

  public WorkflowInstanceVO resume(String executionId) {
    String id = required(executionId);
    return control(
        "WORKFLOW_EXECUTION_RESUME",
        "Resume workflow execution",
        "EXECUTION_RESUMED",
        "Workflow execution resumed",
        id,
        () -> runtime.resume(id));
  }

  public WorkflowInstanceVO cancel(String executionId) {
    String id = required(executionId);
    return control(
        "WORKFLOW_EXECUTION_CANCEL",
        "Cancel workflow execution",
        "EXECUTION_CANCEL_REQUESTED",
        "Workflow execution cancellation requested",
        id,
        () -> runtime.cancel(id));
  }

  private <T> T control(
      String operationType,
      String operationName,
      String changeType,
      String message,
      String executionId,
      Supplier<T> action) {
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                operationType,
                operationName,
                "WORKFLOW_EXECUTION",
                executionId,
                executionId,
                "WEB",
                Map.of("controlType", changeType)));
    try {
      T result = action.get();
      audit.event(
          new AuditEventRequest(
              AuditEventType.RESOURCE_UPDATED,
              AuditEventCategory.BUSINESS,
              AuditEventStatus.SUCCESS,
              "workflow:execution:"
                  + executionId
                  + ":control:"
                  + changeType.toLowerCase(Locale.ROOT),
              "WORKFLOW_EXECUTION",
              executionId,
              message,
              null,
              Map.of("changeType", changeType, "executionId", executionId)));
      audit.success(message);
      return result;
    } catch (RuntimeException exception) {
      audit.failure(operationType + "_FAILED", exception);
      throw exception;
    }
  }

  private String required(String executionId) {
    if (executionId == null || executionId.isBlank()) {
      throw new IllegalArgumentException("工作流实例 ID 不能为空");
    }
    return executionId.trim();
  }
}
