package io.yak.ops.common.bean.vo.workflow;

import java.util.List;

/** 批量失败实例重试结果；单条失败不会回滚整批。 */
public record WorkflowBatchRetryVO(
    int requestedCount,
    int acceptedCount,
    int failedCount,
    List<ItemVO> items) {

  public WorkflowBatchRetryVO {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public record ItemVO(
      String executionId,
      boolean accepted,
      String status,
      String message) {
  }
}
