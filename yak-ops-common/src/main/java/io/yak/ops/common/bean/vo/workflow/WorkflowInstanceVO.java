package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 工作流执行实例展示对象。 */
public record WorkflowInstanceVO(
    String id,
    String definitionId,
    String sourceExecutionId,
    String name,
    String status,
    String failureStrategy,
    Instant startedAt,
    Instant runStartedAt,
    Instant endedAt,
    long workflowTimeoutSeconds,
    Map<String, Object> input,
    int nodeCount,
    int edgeCount,
    List<NodeInstanceVO> nodes,
    String workflowVersionId,
    Integer workflowVersionNo,
    boolean testRun,
    String creatorName) {

  /** Compatibility constructor for existing runtime projections. */
  public WorkflowInstanceVO(
      String id,
      String definitionId,
      String sourceExecutionId,
      String name,
      String status,
      String failureStrategy,
      Instant startedAt,
      Instant runStartedAt,
      Instant endedAt,
      long workflowTimeoutSeconds,
      Map<String, Object> input,
      int nodeCount,
      int edgeCount,
      List<NodeInstanceVO> nodes,
      String workflowVersionId,
      Integer workflowVersionNo,
      boolean testRun) {
    this(
        id, definitionId, sourceExecutionId, name, status, failureStrategy,
        startedAt, runStartedAt, endedAt, workflowTimeoutSeconds, input,
        nodeCount, edgeCount, nodes, workflowVersionId, workflowVersionNo, testRun, null);
  }

  public WorkflowInstanceVO(
      String id,
      String definitionId,
      String sourceExecutionId,
      String name,
      String status,
      String failureStrategy,
      Instant startedAt,
      Instant runStartedAt,
      Instant endedAt,
      long workflowTimeoutSeconds,
      Map<String, Object> input,
      int nodeCount,
      int edgeCount,
      List<NodeInstanceVO> nodes) {
    this(
        id, definitionId, sourceExecutionId, name, status, failureStrategy,
        startedAt, runStartedAt, endedAt, workflowTimeoutSeconds, input,
        nodeCount, edgeCount, nodes, null, null, false, null);
  }

  public WorkflowInstanceVO withCreatorName(String value) {
    return new WorkflowInstanceVO(
        id, definitionId, sourceExecutionId, name, status, failureStrategy,
        startedAt, runStartedAt, endedAt, workflowTimeoutSeconds, input,
        nodeCount, edgeCount, nodes, workflowVersionId, workflowVersionNo, testRun, value);
  }

  public record NodeInstanceVO(
      String id,
      String taskId,
      String name,
      String type,
      String status,
      String triggerRule,
      String failurePolicy,
      String errorMessage,
      String failureReason,
      boolean continuedAfterFailure,
      int attemptCount,
      String currentAttemptId,
      Integer currentAttemptNumber,
      int retryMaxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping,
      Map<String, Object> input,
      Map<String, Map<String, Object>> predecessorOutputs,
      Map<String, Object> output,
      List<AttemptVO> attempts) {
  }

  public record AttemptVO(
      String id,
      int attemptNumber,
      String status,
      String failureReason,
      String errorMessage,
      Instant availableAt,
      Instant startedAt,
      Instant pausedAt,
      long pausedMillis,
      Instant endedAt) {
  }
}
