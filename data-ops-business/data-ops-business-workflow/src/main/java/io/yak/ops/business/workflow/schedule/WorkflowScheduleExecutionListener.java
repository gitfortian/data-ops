package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;

import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** WorkflowExecution 终态提交后完成 Trigger Ledger，并推进 SERIAL_WAIT 队首。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleExecutionListener {
  private final WorkflowScheduleTriggerCoordinator coordinator;

  public WorkflowScheduleExecutionListener(WorkflowScheduleTriggerCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onTerminal(WorkflowExecutionTerminalEvent event) {
    coordinator.completeExecution(
        event.executionId(),
        event.executionStatus(),
        event.endedAt());
  }
}
