package io.yak.ops.business.mdm.domain.clean;

import java.util.Map;

/**
 * 标准化规则表达式(最小化设计,ticket 57):按属性编码配置值映射表,
 * 执行时将记录属性中匹配源值的字段替换为目标值。
 * 例:性别标准化 {"fields":{"gender":{"M":"1","F":"2"}}}
 * 复用 semantic 码值标准定义,MDM 只存映射关系本身。
 */
public record StandardizeExpr(Map<String, Map<String, String>> fields) {

  /** 对记录属性执行标准化;返回值与原值相同时返回 null(表示无需更新)。 */
  public Map<String, Object> apply(Map<String, Object> attributes) {
    if (fields == null || fields.isEmpty()) {
      return null;
    }
    Map<String, Object> result = new java.util.LinkedHashMap<>(attributes);
    boolean changed = false;
    for (Map.Entry<String, Map<String, String>> entry : fields.entrySet()) {
      Object raw = result.get(entry.getKey());
      if (raw == null) {
        continue;
      }
      String target = entry.getValue().get(String.valueOf(raw));
      if (target != null && !target.equals(String.valueOf(raw))) {
        result.put(entry.getKey(), target);
        changed = true;
      }
    }
    return changed ? result : null;
  }

  /** 判断记录是否会被标准化(至少一个属性命中映射源值)。 */
  public boolean wouldChange(Map<String, Object> attributes) {
    if (fields == null || fields.isEmpty()) {
      return false;
    }
    for (Map.Entry<String, Map<String, String>> entry : fields.entrySet()) {
      Object raw = attributes.get(entry.getKey());
      if (raw != null && entry.getValue().containsKey(String.valueOf(raw))) {
        return true;
      }
    }
    return false;
  }
}
