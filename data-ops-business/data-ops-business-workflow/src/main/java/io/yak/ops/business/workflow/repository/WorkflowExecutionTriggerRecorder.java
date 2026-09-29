package io.yak.ops.business.workflow.repository;

import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository.RuntimeMetadataRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 将启动来源补充到新创建 Execution 的持久化 Runtime Metadata。 */
@Component
public class WorkflowExecutionTriggerRecorder {
  private final ObjectProvider<WorkflowRuntimeRepository> runtimePersistence;

  public WorkflowExecutionTriggerRecorder(
      ObjectProvider<WorkflowRuntimeRepository> runtimePersistence) {
    this.runtimePersistence = runtimePersistence;
  }

  public void record(String executionId, WorkflowTriggerContext context) {
    if (executionId == null || executionId.isBlank() || context == null) return;
    WorkflowRuntimeRepository persistence = runtimePersistence.getIfAvailable();
    if (persistence == null) return;

    RuntimeMetadataRecord current = persistence.findMetadata(executionId)
        .orElseThrow(() -> new IllegalStateException("工作流运行元数据不存在：" + executionId));
    RuntimeMetadataRecord updated = new RuntimeMetadataRecord(
        current.name(),
        current.edgeCount(),
        current.workflowTimeoutSeconds(),
        current.failureStrategy(),
        current.workflowVersionId(),
        current.workflowVersionNo(),
        current.testRun(),
        current.nodes(),
        context.triggerType().name(),
        context.triggerId(),
        context.scheduleId(),
        context.plannedFireTime());
    persistence.saveMetadata(executionId, updated);
  }
}
