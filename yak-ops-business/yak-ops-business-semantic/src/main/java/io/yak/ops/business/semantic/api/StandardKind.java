package io.yak.ops.business.semantic.api;

import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.Optional;

/** 六类数据标准判别(DOMAIN.md);类别专有必填校验的唯一归属地。 */
public enum StandardKind {
  NAMING,
  TYPE,
  CODE,
  UNIT,
  CALIBER,
  /*
   * 安全标准=字段级分级/脱敏模板(ticket 01 裁决):level_code 是示例序位,mask_rule 为参考文案——
   * 不做引擎执行。等级字典真源在数据安全模块 yak_dsec_security_level,脱敏执行走 dsec 脱敏算法;
   * 关联桥=dsec 等级经 std_security_id 引用本类标准(SecurityLevelService 校验)。
   */
  SECURITY;

  public static Optional<StandardKind> fromStored(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(StandardKind.valueOf(value));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  /** 类别专有必填校验;缺失抛 KIND_FIELD_REQUIRED,detail 为字段名。 */
  public void validateRequired(Standard.KindFields fields) {
    switch (this) {
      case NAMING -> requireText(fields.ruleExpr(), "rule_expr");
      case TYPE -> {
        requireText(fields.typeCode(), "type_code");
        requireText(fields.stdType(), "std_type");
      }
      case CODE -> {
        requireText(fields.codeSetCode(), "code_set_code");
        requireText(fields.codeValue(), "code_value");
      }
      case UNIT -> requireText(fields.unitCode(), "unit_code");
      case CALIBER -> requireText(fields.calRule(), "cal_rule");
      case SECURITY -> requireText(fields.levelCode(), "level_code");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new SemanticException(SemanticErrorCode.KIND_FIELD_REQUIRED, field);
    }
  }
}
