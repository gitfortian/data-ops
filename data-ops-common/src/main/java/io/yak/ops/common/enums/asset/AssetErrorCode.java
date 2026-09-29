package io.yak.ops.common.enums.asset;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数据资产模块错误码(48001~48099 段)。 */
@Getter
@RequiredArgsConstructor
public enum AssetErrorCode implements ErrorCode {

  ASSET_NOT_FOUND(48001, "资产不存在"),
  DUPLICATE_ASSET_KEY(48002, "资产键已存在"),
  ILLEGAL_STATE_OPERATION(48003, "当前状态不允许该操作"),
  PRECHECK_FAILED(48004, "上架预检未通过且未接受风险"),
  PRECHECK_TOKEN_INVALID(48005, "预检令牌无效或已过期,请重新预检"),
  DIRECTORY_INVALID(48006, "目录不存在或移动将产生环"),
  DIRECTORY_NOT_EMPTY(48007, "目录非空(有子目录或资产),不可删除"),
  TEMPLATE_ALREADY_INITIALIZED(48008, "目录模板已初始化"),
  DUPLICATE_TAG_CODE(48009, "标签编码已存在"),
  RULE_DRY_RUN_REQUIRED(48010, "编目规则必须先试跑通过才能启用"),
  PROVIDER_UNAVAILABLE(48011, "来源域暂不可用"),
  RECONCILE_RUNNING(48012, "对账进行中,请稍后再试"),
  OFFLINE_REASON_REQUIRED(48013, "下架原因必填"),
  CHANGE_ALREADY_HANDLED(48014, "变更记录已处理,请勿重复操作"),
  MANUAL_NOT_RECONCILABLE(48015, "手工登记资产不参与对账"),
  NOT_IGNORABLE(48016, "仅待上架/已下架资产可忽略,已上架资产请先下架"),
  INVALID_ARGUMENT(48017, "参数不合法");

  private final Integer code;
  private final String message;
}
