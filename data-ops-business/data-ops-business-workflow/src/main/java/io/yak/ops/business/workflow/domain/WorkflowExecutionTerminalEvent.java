package io.yak.ops.business.workflow.domain;

import java.time.Instant;

/** WorkflowExecution 进入终态后的事务提交事件。 */
public record WorkflowExecutionTerminalEvent(
    String executionId,
    String executionStatus,
    Instant endedAt) {
}
