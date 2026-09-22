package io.yak.ops.business.approval.api;

/** 业务发起/查询入口(同事务;flowCode 未配置或停用直接抛错,不静默)。由业务模块注入使用。 */
public interface ApprovalApi {

  /** 发起审批:flowCode 无 handler → 49007;流程停用 → 49002;同 biz 在途重复 → 49003。 */
  ApprovalInstanceView submit(ApprovalSubmitCommand cmd);

  /** 该业务对象的在途单;无在途则返回最近一单;从未发起返回 null。 */
  ApprovalInstanceView find(String flowCode, String bizType, String bizId);

  /** 撤销,仅发起人可操作;终态回调 onCanceled 同事务执行。 */
  void cancel(Long instanceId, String operator, String reason);
}
