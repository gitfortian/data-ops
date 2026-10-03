package io.yak.ops.business.mdm.application;

import io.yak.ops.business.mdm.domain.clean.MdmDedupKey;
import io.yak.ops.business.mdm.domain.clean.CleanJson;
import io.yak.ops.business.mdm.domain.clean.CompleteExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import io.yak.ops.business.mdm.domain.clean.MdmDedupIgnore;
import io.yak.ops.business.mdm.domain.clean.MdmMatchField;
import io.yak.ops.business.mdm.domain.clean.MdmMatchType;
import io.yak.ops.business.mdm.domain.clean.MdmMergeLog;
import io.yak.ops.business.mdm.domain.clean.StandardizeExpr;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.processing.DedupSql;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure cleansing expression validation and transformation; persistence and audit stay in the use case. */
final class CleanRulePolicy {
  private CleanRulePolicy() {}

  /** 按规则类型分发表达式解析与执行;未知类型抛异常。 */
  static Map<String, Object> apply(MdmCleanRule rule, Map<String, Object> attrs) {
    return switch (rule.ruleType()) {
      case STANDARDIZE -> {
        StandardizeExpr expr;
        try {
          expr = CleanJson.parseStandardizeExpr(rule.ruleExpr());
        } catch (IllegalArgumentException exception) {
          throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
        }
        yield expr.apply(attrs);
      }
      case COMPLETE -> {
        CompleteExpr expr;
        try {
          expr = CleanJson.parseCompleteExpr(rule.ruleExpr());
        } catch (IllegalArgumentException exception) {
          throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
        }
        yield expr.apply(attrs);
      }
      default -> throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE,
          "规则类型 " + rule.ruleType() + " 不支持标准化/补全执行");
    };
  }

  static void validate(MdmCleanRuleType ruleType, String ruleExprJson, Set<String> validCodes, Set<String> pkCodes) {
    switch (ruleType) {
      case DEDUP -> validateDedupExpr(ruleExprJson, validCodes);
      case STANDARDIZE -> validateStandardizeExpr(ruleExprJson, validCodes, pkCodes);
      case COMPLETE -> validateCompleteExpr(ruleExprJson, validCodes, pkCodes);
    }
  }

  private static void validateDedupExpr(String json, Set<String> validCodes) {
    MdmCleanRuleExpr expr;
    try {
      expr = CleanJson.parseExpr(json);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
    }
    if (expr.fields() == null || expr.fields().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个匹配字段");
    }
    Set<String> seen = new HashSet<>();
    for (MdmMatchField field : expr.fields()) {
      if (field == null || field.attrCode() == null || field.attrCode().isBlank()) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "匹配字段编码不能为空");
      }
      if (!DedupSql.isValidAttrCode(field.attrCode())) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "非法属性编码: " + field.attrCode());
      }
      if (!validCodes.contains(field.attrCode())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "匹配字段必须是实体已定义属性: " + field.attrCode());
      }
      if (!seen.add(field.attrCode())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "同一字段仅允许出现一次: " + field.attrCode());
      }
      if (field.matchType() == null) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "匹配方式不能为空");
      }
    }
  }

  private static void validateStandardizeExpr(
      String json, Set<String> validCodes, Set<String> pkCodes) {
    StandardizeExpr expr;
    try {
      expr = CleanJson.parseStandardizeExpr(json);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
    }
    if (expr.fields() == null || expr.fields().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个标准化字段");
    }
    for (Map.Entry<String, Map<String, String>> entry : expr.fields().entrySet()) {
      if (!validCodes.contains(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段必须是实体已定义属性: " + entry.getKey());
      }
      rejectPkTransform(entry.getKey(), pkCodes);
      if (!DedupSql.isValidAttrCode(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "非法属性编码: " + entry.getKey());
      }
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段 " + entry.getKey() + " 的值映射不能为空");
      }
    }
  }

  private static void validateCompleteExpr(
      String json, Set<String> validCodes, Set<String> pkCodes) {
    CompleteExpr expr;
    try {
      expr = CleanJson.parseCompleteExpr(json);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
    }
    if (expr.defaults() == null || expr.defaults().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个补全默认值");
    }
    for (Map.Entry<String, String> entry : expr.defaults().entrySet()) {
      if (!validCodes.contains(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段必须是实体已定义属性: " + entry.getKey());
      }
      rejectPkTransform(entry.getKey(), pkCodes);
      if (!DedupSql.isValidAttrCode(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "非法属性编码: " + entry.getKey());
      }
      if (entry.getValue() == null) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段 " + entry.getKey() + " 的默认值不能为空");
      }
    }
  }

  private static void rejectPkTransform(String attributeCode, Set<String> pkCodes) {
    if (pkCodes.contains(attributeCode)) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE,
          "PK 属性参与 master_id 身份计算，不能通过清洗规则修改: " + attributeCode);
    }
  }
}
