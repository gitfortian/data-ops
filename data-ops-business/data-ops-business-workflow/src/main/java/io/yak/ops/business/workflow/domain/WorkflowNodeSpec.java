package io.yak.ops.business.workflow.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 工作流节点领域规格。 */
public record WorkflowNodeSpec(
    String id,
    String taskId,
    Long taskAssetId,
    Long taskRevisionId,
    Integer taskRevisionNo,
    double positionX,
    double positionY,
    int maxAttempts,
    long retryDelaySeconds,
    long dispatchTimeoutSeconds,
    long executionTimeoutSeconds,
    Map<String, String> inputMapping,
    String triggerRule,
    String failurePolicy) {

  public WorkflowNodeSpec {
    boolean hasCatalogBinding = taskAssetId != null || taskRevisionId != null || taskRevisionNo != null;
    if (hasCatalogBinding) {
      if (taskAssetId == null || taskRevisionId == null || taskRevisionNo == null) {
        throw new IllegalArgumentException("工作流任务资产绑定必须同时包含 asset/revision/revisionNo");
      }
      if (taskAssetId <= 0L || taskRevisionId <= 0L || taskRevisionNo <= 0) {
        throw new IllegalArgumentException("工作流任务资产绑定 ID/版本号必须大于 0");
      }
      taskId = catalogTaskId(taskAssetId);
    } else if (taskId == null || taskId.isBlank()) {
      throw new IllegalArgumentException("taskId 不能为空");
    } else {
      taskId = taskId.trim();
    }
    inputMapping = inputMapping == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(inputMapping));
    triggerRule = triggerRule == null || triggerRule.isBlank() ? "ALL_SUCCESS" : triggerRule;
    failurePolicy = failurePolicy == null || failurePolicy.isBlank()
        ? "FAIL_WORKFLOW"
        : failurePolicy;
  }

  /** Backward-compatible constructor for legacy TaskRegistry nodes. */
  public WorkflowNodeSpec(
      String id,
      String taskId,
      double positionX,
      double positionY,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping,
      String triggerRule,
      String failurePolicy) {
    this(
        id,
        taskId,
        null,
        null,
        null,
        positionX,
        positionY,
        maxAttempts,
        retryDelaySeconds,
        dispatchTimeoutSeconds,
        executionTimeoutSeconds,
        inputMapping,
        triggerRule,
        failurePolicy);
  }

  public boolean catalogBound() {
    return taskAssetId != null;
  }

  public WorkflowNodeSpec withTaskRevision(long revisionId, int revisionNo) {
    if (!catalogBound()) throw new IllegalStateException("当前节点不是 TaskAsset 节点");
    return new WorkflowNodeSpec(
        id,
        catalogTaskId(taskAssetId),
        taskAssetId,
        revisionId,
        revisionNo,
        positionX,
        positionY,
        maxAttempts,
        retryDelaySeconds,
        dispatchTimeoutSeconds,
        executionTimeoutSeconds,
        inputMapping,
        triggerRule,
        failurePolicy);
  }

  public static String catalogTaskId(long taskAssetId) {
    return "task-asset:" + taskAssetId;
  }
}
