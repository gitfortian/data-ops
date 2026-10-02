package io.yak.ops.common.enums.semantic;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 业务语义模块业务错误码(42001+ 段;41001/41901/43001 已占用)。 */
@Getter
@RequiredArgsConstructor
public enum SemanticErrorCode implements ErrorCode {

  NOT_FOUND(42001, "标准不存在"),
  DUPLICATE_CODE(42002, "标准编码已存在"),
  INVALID_KIND(42003, "标准类别不合法"),
  INVALID_CODE(42004, "标准编码格式不合法"),
  INVALID_NAME(42005, "标准名称不合法"),
  CREATE_FAILED(42006, "创建标准失败"),
  UPDATE_FAILED(42007, "更新标准失败"),
  DELETE_FAILED(42008, "删除标准失败"),
  INVALID_STATUS(42009, "标准状态不合法"),
  STANDARD_REFERENCED(42010, "标准已被引用,无法执行该操作"),
  KIND_FIELD_REQUIRED(42011, "类别专有字段缺失"),
  INVALID_SEARCH(42012, "查询条件不合法"),
  INVALID_MOVE(42013, "移动目标不合法"),
  VERSION_CONFLICT(42014, "数据已被他人修改,请刷新后重试"),
  CODE_SET_NOT_FOUND(42015, "码集不存在或已停用"),
  CODE_SET_DUPLICATE(42016, "码集编码已存在"),
  CODE_SET_VALUE_DUPLICATE(42017, "同一码集内码值重复"),
  ROLE_FIELD_REQUIRED(42018, "当前角色必填的引用缺失"),
  LAYER_CONFIG_INVALID(42019, "分层配置不合法"),
  LAYER_REFERENCED(42020, "分层已被模型引用,无法删除"),
  LAYER_PRESET_DELETE_BLOCKED(42021, "默认分层不可删除"),
  PRESET_DELETE_BLOCKED(42022, "平台预置标准不可删除"),
  PUBLISH_ALREADY_ENABLED(42023, "标准已启用,无需提交生效审批"),
  // 删除拦截文案必须点明被删对象:复用 STANDARD_REFERENCED 会让删业务域/业务过程时
  // 提示"标准已被引用",术语错误且把用户引向错误的排查方向。
  DOMAIN_REFERENCED(42024, "业务域已被引用,无法删除"),
  PROCESS_REFERENCED(42025, "业务过程已被引用,无法删除"),
  FIELD_REFERENCED(42026, "标准字段已被引用,无法删除");

  private final Integer code;
  private final String message;
}
