package io.yak.ops.common.enums.approval;

/** 审批单状态:在途唯一(active_flag),终态不可再变更。 */
public enum ApprovalInstanceStatus {
  PENDING,
  APPROVED,
  REJECTED,
  CANCELED
}
