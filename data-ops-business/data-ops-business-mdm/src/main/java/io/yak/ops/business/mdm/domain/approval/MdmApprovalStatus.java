package io.yak.ops.business.mdm.domain.approval;

/** 主数据审批状态(ticket 60)。 */
public enum MdmApprovalStatus {
  PENDING,
  APPROVED,
  REJECTED,
  WITHDRAWN;

  public boolean isTerminal() {
    return this == APPROVED || this == REJECTED || this == WITHDRAWN;
  }
}
