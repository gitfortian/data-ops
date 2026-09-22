package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Projects committed WorkflowExecution terminal truth into the durable business audit timeline. */
@Component
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowExecutionAuditTerminalListener {
  private final WorkflowExecutionAuditBridge auditBridge;

  public WorkflowExecutionAuditTerminalListener(WorkflowExecutionAuditBridge auditBridge) {
    this.auditBridge = auditBridge;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onTerminal(WorkflowExecutionTerminalEvent event) {
    auditBridge.observeTerminal(event);
  }
}
