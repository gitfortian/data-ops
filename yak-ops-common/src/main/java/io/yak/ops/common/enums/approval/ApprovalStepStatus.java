package io.yak.ops.common.enums.approval;

/**
 * 审批步骤状态。不变量:单据进入终态时所有剩余 WAITING/PENDING 统一置 SKIPPED,
 * 故 step=PENDING ⇔ 单据在途且轮到该审批人(待办查询免 join)。
 */
public enum ApprovalStepStatus {
  WAITING,
  PENDING,
  APPROVED,
  REJECTED,
  SKIPPED
}
