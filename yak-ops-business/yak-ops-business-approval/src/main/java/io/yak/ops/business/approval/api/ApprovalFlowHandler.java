package io.yak.ops.business.approval.api;

/**
 * 业务模块实现的终态回调 SPI(按 flowCode 注册,同 AssetProvider 范式,D2 依赖反转)。
 * 回调与审批操作同事务同步执行(D5):抛错则整体回滚,审批动作失败可见(49009),由审批人重试。
 * 实现内禁止再调审批中心写接口(重入),禁止跨进程 HTTP。
 */
public interface ApprovalFlowHandler {

  String flowCode();

  /** 业务生效点. */
  void onApproved(ApprovalDecision decision);

  default void onRejected(ApprovalDecision decision) {}

  default void onCanceled(ApprovalDecision decision) {}
}
