package io.yak.ops.common.enums.approval;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通用审批流模块错误码(49001~49099 段)。 */
@Getter
@RequiredArgsConstructor
public enum ApprovalErrorCode implements ErrorCode {

  FLOW_NOT_FOUND(49001, "审批流程不存在或未配置"),
  FLOW_DISABLED(49002, "审批流程已停用"),
  DUPLICATE_IN_FLIGHT(49003, "该业务对象已有在途审批单,请先处理"),
  NOT_CURRENT_APPROVER(49004, "您不是该审批单当前级的审批人"),
  ILLEGAL_STATE_OR_OPERATOR(49005, "审批单状态或操作人不允许该操作"),
  REJECT_COMMENT_REQUIRED(49006, "拒绝必须填写审批意见"),
  HANDLER_NOT_REGISTERED(49007, "业务流程未注册回调处理器"),
  NOT_INVOLVED(49008, "仅发起人与各级审批人可见该审批单"),
  CALLBACK_FAILED(49009, "业务回调执行失败,审批操作已回滚"),
  PAYLOAD_TOO_LARGE(49010, "审批依据快照超过 64KB 上限"),
  INVALID_ARGUMENT(49011, "参数不合法");

  private final Integer code;
  private final String message;
}
