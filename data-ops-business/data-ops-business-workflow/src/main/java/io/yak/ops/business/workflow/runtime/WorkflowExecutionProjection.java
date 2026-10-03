package io.yak.ops.business.workflow.runtime;

import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.definition.TriggerRule;
import io.yak.framework.workflow.engine.execution.NodeAttempt;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.spi.NodeDispatch;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO.AttemptVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO.NodeInstanceVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the HTTP read model from immutable engine snapshots and runtime metadata. */
final class WorkflowExecutionProjection {
  private WorkflowExecutionProjection() {}
  static WorkflowInstanceVO toView(WorkflowExecution execution, WorkflowExecutionMetadata runMetadata, Map<String, NodeDispatch> dispatches) {
    List<NodeInstanceVO> nodes = execution.nodes().values().stream()
        .map(node -> toNodeView(node, runMetadata.nodes().get(node.nodeId()), dispatches == null ? null : dispatches.get(node.nodeId())))
        .toList();
    return new WorkflowInstanceVO(
        execution.id(),
        execution.definitionId(),
        execution.sourceExecutionId(),
        runMetadata.name(),
        execution.status().name(),
        runMetadata.failureStrategy(),
        execution.createdAt(),
        execution.runStartedAt(),
        execution.endedAt(),
        runMetadata.workflowTimeoutSeconds(),
        execution.input(),
        nodes.size(),
        runMetadata.edgeCount(),
        nodes,
        runMetadata.workflowVersionId(),
        runMetadata.workflowVersionNo(),
        runMetadata.testRun());
  }

  private static NodeInstanceVO toNodeView(
      NodeExecution node,
      NodeMetadata nodeMetadata,
      NodeDispatch dispatch) {
    TaskVersionSnapshot task = nodeMetadata == null ? null : nodeMetadata.task();
    List<AttemptVO> attempts = node.attempts().stream().map(WorkflowExecutionProjection::toAttemptView).toList();
    NodeAttempt attempt = node.attempts().isEmpty()
        ? null
        : node.attempts().get(node.attempts().size() - 1);
    Map<String, Object> resolvedInput = dispatch == null
        ? receivedInput(node.output())
        : dispatch.nodeInput();
    return new NodeInstanceVO(
        node.nodeId(),
        task == null ? null : task.taskId(),
        task == null ? node.nodeId() : task.name(),
        task == null ? "UNKNOWN" : task.type(),
        node.status().name(),
        nodeMetadata == null ? TriggerRule.ALL_SUCCESS.name() : nodeMetadata.triggerRule(),
        nodeMetadata == null ? NodeFailurePolicy.FAIL_WORKFLOW.name() : nodeMetadata.failurePolicy(),
        node.errorMessage(),
        attempt == null || attempt.failureReason() == null
            ? null
            : attempt.failureReason().name(),
        node.downstreamContinuationAllowed(),
        attempts.size(),
        attempt == null ? null : attempt.id(),
        attempt == null ? null : attempt.attemptNumber(),
        nodeMetadata == null ? 1 : nodeMetadata.maxAttempts(),
        nodeMetadata == null ? 0L : nodeMetadata.retryDelaySeconds(),
        nodeMetadata == null ? 0L : nodeMetadata.dispatchTimeoutSeconds(),
        nodeMetadata == null ? 0L : nodeMetadata.executionTimeoutSeconds(),
        nodeMetadata == null ? Map.of() : nodeMetadata.inputMapping(),
        resolvedInput,
        dispatch == null ? Map.of() : dispatch.predecessorOutputs(),
        node.output(),
        attempts);
  }

  private static Map<String, Object> receivedInput(Map<String, Object> output) {
    if (output == null || !(output.get("receivedInput") instanceof Map<?, ?> values)) {
      return Map.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    values.forEach((key, value) -> {
      if (key instanceof String name) result.put(name, value);
    });
    return Map.copyOf(result);
  }

  private static AttemptVO toAttemptView(NodeAttempt attempt) {
    return new AttemptVO(
        attempt.id(),
        attempt.attemptNumber(),
        attempt.status().name(),
        attempt.failureReason() == null ? null : attempt.failureReason().name(),
        attempt.errorMessage(),
        attempt.availableAt(),
        attempt.startedAt(),
        attempt.pausedAt(),
        attempt.pausedDuration().toMillis(),
        attempt.endedAt());
  }

}
