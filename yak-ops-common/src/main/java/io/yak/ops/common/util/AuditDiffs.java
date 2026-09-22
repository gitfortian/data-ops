package io.yak.ops.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 审计 before/after 差异构造件（多版本契约 C5）：写 {@code *_UPDATE} 审计时必须携带
 * 变更字段的真实旧值/新值，密钥类字段值一律掩码。
 *
 * <p>产出结构（作为审计 payload 的一个条目或全量使用）：
 * <pre>{@code {"changedFields": [..], "before": {仅变更键}, "after": {仅变更键}}}</pre>
 */
public final class AuditDiffs {

  private static final Set<String> SENSITIVE_HINTS =
      Set.of("password", "secret", "token", "credential", "accesskey", "access_key",
          "privatekey", "private_key", "apikey", "api_key");
  private static final String MASKED = "***";

  private AuditDiffs() {}

  /** before/after 均可为 null（创建/删除场景），按空 Map 处理。 */
  public static Map<String, Object> diff(Map<String, ?> before, Map<String, ?> after) {
    Map<String, ?> old = before == null ? Map.of() : before;
    Map<String, ?> next = after == null ? Map.of() : after;
    Set<String> keys = new LinkedHashSet<>(old.keySet());
    keys.addAll(next.keySet());

    Map<String, Object> changedBefore = new LinkedHashMap<>();
    Map<String, Object> changedAfter = new LinkedHashMap<>();
    for (String key : keys) {
      Object oldValue = old.get(key);
      Object newValue = next.get(key);
      boolean same = (oldValue == null ? newValue == null : Objects.equals(oldValue, newValue));
      if (!same) {
        changedBefore.put(key, maskIfNeeded(key, oldValue));
        changedAfter.put(key, maskIfNeeded(key, newValue));
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("changedFields", new ArrayList<>(changedBefore.keySet()));
    result.put("before", changedBefore);
    result.put("after", changedAfter);
    return result;
  }

  /** 整体掩码（不落 diff、只想记快照时使用）。 */
  public static Map<String, Object> mask(Map<String, ?> payload) {
    Map<String, Object> masked = new LinkedHashMap<>();
    if (payload != null) {
      payload.forEach((key, value) -> masked.put(key, maskIfNeeded(key, value)));
    }
    return masked;
  }

  private static Object maskIfNeeded(String key, Object value) {
    if (value == null) {
      return null;
    }
    String lower = key.toLowerCase();
    for (String hint : SENSITIVE_HINTS) {
      if (lower.contains(hint)) {
        return MASKED;
      }
    }
    return value;
  }
}
