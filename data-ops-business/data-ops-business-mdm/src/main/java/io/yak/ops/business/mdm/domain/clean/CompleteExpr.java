package io.yak.ops.business.mdm.domain.clean;

import java.util.Map;

/**
 * 补全规则表达式(最小化设计,ticket 57):按属性编码配置默认值,
 * 执行时将缺失/空值的属性填入默认值。
 * 例:等级补全 {"defaults":{"grade":"普通"}}
 * 跨来源补全(从 AUXILIARY 源取值)为后续增量,本期仅落默认值策略。
 */
public record CompleteExpr(Map<String, String> defaults) {

  /** 对记录属性执行补全;返回值与原值相同时返回 null(表示无需更新)。 */
  public Map<String, Object> apply(Map<String, Object> attributes) {
    if (defaults == null || defaults.isEmpty()) {
      return null;
    }
    Map<String, Object> result = new java.util.LinkedHashMap<>(attributes);
    boolean changed = false;
    for (Map.Entry<String, String> entry : defaults.entrySet()) {
      Object current = result.get(entry.getKey());
      if (current == null || String.valueOf(current).trim().isEmpty()) {
        result.put(entry.getKey(), entry.getValue());
        changed = true;
      }
    }
    return changed ? result : null;
  }

  /** 判断记录是否会被补全(至少一个属性缺失/空值且有默认值)。 */
  public boolean wouldChange(Map<String, Object> attributes) {
    if (defaults == null || defaults.isEmpty()) {
      return false;
    }
    for (Map.Entry<String, String> entry : defaults.entrySet()) {
      Object current = attributes.get(entry.getKey());
      if (current == null || String.valueOf(current).trim().isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
