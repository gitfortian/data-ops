package io.yak.ops.business.approval.domain;

import java.util.Set;

/**
 * 审批流转纯规则(D7 级间串行、同级 ANY)。收敛为单点判定,避免服务层散落状态字面量。
 * 终态清理不变式:单据进入任一终态时剩余 WAITING/PENDING 步骤统一 SKIPPED,
 * 由此 step.status=PENDING ⇔ 单据在途且轮到该行(待办只打 step 表)。
 */
public final class ApprovalStateMachine {

  public static final Set<String> OPEN_STEP_STATUSES = Set.of("WAITING", "PENDING");

  /** approve 第 currentLevel 级后的去向:还有更高级→推进;否则→终态 APPROVED。 */
  public record OnApprove(boolean finished, int nextLevelNo) {}

  public static OnApprove onApprove(int currentLevel, int maxLevel) {
    return currentLevel < maxLevel
        ? new OnApprove(false, currentLevel + 1)
        : new OnApprove(true, currentLevel);
  }

  private ApprovalStateMachine() {}
}
