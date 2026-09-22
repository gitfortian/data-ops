package io.yak.ops.business.audit;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import java.util.Map;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "事务提交后再落审计"规则的唯一归属（收敛自 8 个业务模块 support/audit 下的逐字复制，S3）。
 * 事务外立即落账；事务内延迟到 commit 后落账，回滚则记 failure。
 */
public final class AuditTransactions {

  private AuditTransactions() {}

  public static void completeOnCommit(
      AuditOperationHandle audit,
      AuditEventType eventType,
      String message,
      Map<String, ?> payload,
      String summary) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      audit.event(eventType, message, payload);
      audit.success(summary);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            audit.event(eventType, message, payload);
            audit.success(summary);
          }

          @Override
          public void afterCompletion(int status) {
            if (status != TransactionSynchronization.STATUS_COMMITTED) {
              audit.failure("TRANSACTION_ROLLED_BACK", null);
            }
          }
        });
  }
}
