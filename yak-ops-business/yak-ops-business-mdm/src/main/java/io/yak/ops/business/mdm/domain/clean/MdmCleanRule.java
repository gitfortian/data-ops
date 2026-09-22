package io.yak.ops.business.mdm.domain.clean;

import java.time.LocalDateTime;

/** 主数据清洗规则(实体级配置);rule_expr 为规则表达式 JSON 文本。 */
public record MdmCleanRule(
    Long id,
    Long entityId,
    MdmCleanRuleType ruleType,
    String ruleName,
    String ruleExpr,
    boolean enabled,
    int sortOrder,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public MdmCleanRule withPersisted(Long id, String operator, LocalDateTime time) {
    return new MdmCleanRule(
        id, entityId, ruleType, ruleName, ruleExpr, enabled, sortOrder, operator, time, time);
  }

  public MdmCleanRule withEditable(String ruleName, String ruleExpr, int sortOrder) {
    return new MdmCleanRule(
        id, entityId, ruleType, ruleName, ruleExpr, enabled, sortOrder, createdBy, createTime,
        updateTime);
  }

  public MdmCleanRule withEnabled(boolean enabled) {
    return new MdmCleanRule(
        id, entityId, ruleType, ruleName, ruleExpr, enabled, sortOrder, createdBy, createTime,
        updateTime);
  }
}
