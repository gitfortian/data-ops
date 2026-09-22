package io.yak.ops.business.mdm.controller.v1.vo;

import io.yak.ops.business.mdm.domain.clean.CleanJson;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import io.yak.ops.business.mdm.domain.clean.MdmMatchField;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 清洗规则视图:rule_expr 已解析为结构化字段(去重)或原始 JSON(标准化/补全),
 * 便于前端直接渲染。ticket 57 扩展支持全部规则类型。
 */
public record MdmCleanRuleVO(
    Long id,
    Long entityId,
    String ruleType,
    String ruleName,
    /** 仅 DEDUP 类型有值,STANDARDIZE/COMPLETE 为 null。 */
    List<MdmMatchField> fields,
    /** 仅 DEDUP 类型有值。 */
    String condition,
    /** 原始规则表达式 JSON,所有类型均有值。 */
    String rawExpr,
    boolean enabled,
    int sortOrder,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static MdmCleanRuleVO from(MdmCleanRule rule) {
    if (rule.ruleType() == MdmCleanRuleType.DEDUP) {
      MdmCleanRuleExpr expr = parseSafely(rule.ruleExpr());
      return new MdmCleanRuleVO(
          rule.id(), rule.entityId(), rule.ruleType().name(), rule.ruleName(),
          expr == null ? List.of() : expr.fields(),
          expr == null || expr.condition() == null ? "AND" : expr.condition(),
          rule.ruleExpr(),
          rule.enabled(), rule.sortOrder(), rule.createdBy(),
          rule.createTime(), rule.updateTime());
    }
    // STANDARDIZE/COMPLETE:返回原始 JSON,前端按 ruleType 解析
    return new MdmCleanRuleVO(
        rule.id(), rule.entityId(),
        rule.ruleType() == null ? null : rule.ruleType().name(),
        rule.ruleName(),
        null, null, rule.ruleExpr(),
        rule.enabled(), rule.sortOrder(), rule.createdBy(),
        rule.createTime(), rule.updateTime());
  }

  /** DB 内规则表达式由服务层写入,解析失败时按空表达式展示(不阻断列表)。 */
  private static MdmCleanRuleExpr parseSafely(String ruleExpr) {
    if (ruleExpr == null || ruleExpr.isBlank()) {
      return null;
    }
    try {
      return CleanJson.parseExpr(ruleExpr);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
