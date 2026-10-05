package io.yak.ops.common.enums.security;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数据安全模块业务错误码(45001~45099 段;44xxx 归主数据/指标,46xxx 归告警)。 */
@Getter
@RequiredArgsConstructor
public enum SecurityErrorCode implements ErrorCode {

  DELETE_FAILED(45001, "数据安全删除失败"),
  INVALID_STATUS(45002, "状态不合法"),
  NOT_FOUND(45003, "记录不存在"),

  LEVEL_NOT_FOUND(45010, "安全等级不存在"),
  LEVEL_DUPLICATE_CODE(45011, "安全等级编码已存在"),
  LEVEL_INVALID_CODE(45012, "安全等级编码不合法"),
  LEVEL_INVALID_NAME(45013, "安全等级名称不合法"),
  LEVEL_REFERENCED(45014, "安全等级已被分级标签引用,无法删除"),
  LEVEL_INVALID_RANK(45015, "安全等级序位不合法"),
  LEVEL_INVALID_STD_SECURITY(45016, "引用的 SECURITY 数据标准不存在或未启用"),

  CATEGORY_NOT_FOUND(45020, "数据分类不存在"),
  CATEGORY_DUPLICATE_CODE(45021, "数据分类编码已存在"),
  CATEGORY_INVALID_CODE(45022, "数据分类编码不合法"),
  CATEGORY_PARENT_NOT_FOUND(45023, "上级数据分类不存在"),
  CATEGORY_HAS_CHILDREN(45024, "存在子分类,无法删除"),
  CATEGORY_REFERENCED(45025, "数据分类已被分级标签引用,无法删除"),

  CLASSIFICATION_NOT_FOUND(45030, "资产分级标签不存在"),
  CLASSIFICATION_INVALID_LEVEL(45031, "引用的安全等级不存在或未启用"),
  CLASSIFICATION_INVALID_CATEGORY(45032, "引用的数据分类不存在"),
  CLASSIFICATION_INVALID_OBJECT(45033, "数据对象定位不合法"),
  CLASSIFICATION_INVALID_STATUS(45034, "分级标签状态不合法"),

  DISCOVERY_RULE_NOT_FOUND(45040, "敏感发现规则不存在"),
  DISCOVERY_RULE_DUPLICATE_CODE(45041, "敏感发现规则编码已存在"),
  DISCOVERY_INVALID_PATTERN(45042, "匹配模式不合法或无法编译"),
  DISCOVERY_INVALID_MATCH_TYPE(45043, "匹配方式不合法"),

  ACCESS_POLICY_NOT_FOUND(45050, "数据访问策略不存在"),
  ACCESS_INVALID_SUBJECT(45051, "访问主体类型不合法"),
  ACCESS_INVALID_SCOPE(45052, "访问范围类型不合法"),
  ACCESS_INVALID_ACTION(45053, "访问动作不合法"),
  ACCESS_NOT_PENDING(45054, "策略不处于待审批状态"),
  ACCESS_APPROVAL_SNAPSHOT_STALE(45055, "策略已偏离送审依据，请撤销后重新提交"),

  MASKING_ALGO_NOT_FOUND(45060, "脱敏算法不存在"),
  MASKING_ALGO_DUPLICATE(45061, "脱敏算法编码已存在"),
  MASKING_ALGO_BUILTIN_READONLY(45062, "内置脱敏算法不可删除"),
  MASKING_POLICY_NOT_FOUND(45063, "脱敏策略不存在"),
  MASKING_INVALID_ALGO(45064, "引用的脱敏算法不存在"),
  MASKING_UNSUPPORTED_ALGO(45065, "不支持的脱敏算法"),

  COMPLIANCE_RULE_NOT_FOUND(45080, "合规规则不存在"),
  COMPLIANCE_RULE_DUPLICATE_CODE(45081, "合规规则编码已存在"),
  COMPLIANCE_INVALID_RULE_TYPE(45082, "合规规则类型不合法"),
  COMPLIANCE_NO_ENABLED_RULE(45083, "没有启用中的合规规则可执行体检");

  private final Integer code;
  private final String message;
}
