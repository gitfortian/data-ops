package io.yak.ops.business.mdm.domain.clean;

import java.util.List;

/**
 * 去重规则表达式:匹配字段列表 + 组合条件(AND 全部命中 / OR 任一命中)。
 * 序列化为 rule_expr JSON 文本落库;字段必须为实体已定义属性且不重复。
 */
public record MdmCleanRuleExpr(List<MdmMatchField> fields, String condition) {

  public static final String CONDITION_AND = "AND";
  public static final String CONDITION_OR = "OR";

  /** 组合条件缺省为 AND。 */
  public boolean isAnd() {
    return condition == null || CONDITION_AND.equalsIgnoreCase(condition.trim());
  }
}
