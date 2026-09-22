package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** 工作流历史补数批次及 Trigger Ledger 汇总。 */
public record WorkflowBackfillVO(
    String id,
    String workflowId,
    String workflowVersionId,
    Integer workflowVersionNo,
    String scheduleId,
    String scheduleName,
    String name,
    String status,
    String operationType,
    String sourceExecutionId,
    LocalDate startBusinessDate,
    LocalDate endBusinessDate,
    String cronExpression,
    String timezone,
    String executionStrategy,
    Map<String, Object> input,
    int totalCount,
    int waitingCount,
    int runningCount,
    int succeededCount,
    int failedCount,
    int canceledCount,
    int skippedCount,
    Instant createTime,
    Instant updateTime) {

  public WorkflowBackfillVO {
    input = input == null ? Map.of() : Map.copyOf(input);
  }
}
