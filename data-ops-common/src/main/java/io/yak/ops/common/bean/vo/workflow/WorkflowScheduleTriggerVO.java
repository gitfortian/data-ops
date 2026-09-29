package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.time.LocalDate;

/** 工作流调度 Trigger Ledger 视图。 */
public record WorkflowScheduleTriggerVO(
    String id,
    String scheduleId,
    String workflowId,
    String backfillId,
    String triggerId,
    String triggerSource,
    LocalDate businessDate,
    Instant plannedFireTime,
    Instant actualFireTime,
    String executionStrategy,
    String misfireStrategy,
    String status,
    String workflowExecutionId,
    String executionStatus,
    String message,
    String errorMessage,
    Instant launchedAt,
    Instant completedAt,
    Instant createTime,
    Instant updateTime) {
}
