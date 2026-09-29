package io.yak.ops.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDiffsTest {

  @Test
  void reportsOnlyChangedFields() {
    Map<String, Object> result =
        AuditDiffs.diff(Map.of("name", "a", "keep", "k"), Map.of("name", "b", "keep", "k"));
    assertThat(result.get("changedFields")).isEqualTo(java.util.List.of("name"));
    assertThat(asMap(result.get("before"))).containsEntry("name", "a");
    assertThat(asMap(result.get("after"))).containsEntry("name", "b");
  }

  @Test
  void handlesCreationAndDeletionSides() {
    Map<String, Object> created = AuditDiffs.diff(null, Map.of("name", "x"));
    // LinkedHashMap.put(k, null) 与 "无此键" 在 equals 语义下不同，按显式 null 断言
    assertThat(asMap(created.get("before"))).containsOnlyKeys("name");
    assertThat(asMap(created.get("before")).get("name")).isNull();
    assertThat(asMap(created.get("after"))).containsEntry("name", "x");
  }

  @Test
  void masksSensitiveValues() {
    Map<String, Object> result =
        AuditDiffs.diff(Map.of("dbPassword", "old"), Map.of("dbPassword", "new"));
    assertThat(asMap(result.get("after"))).containsEntry("dbPassword", "***");
    assertThat(asMap(result.get("before"))).containsEntry("dbPassword", "***");
  }

  @Test
  void newKeyAppearsWithNullOldValue() {
    Map<String, Object> result = AuditDiffs.diff(Map.of("a", 1), Map.of("a", 1, "b", 2));
    assertThat(asMap(result.get("before"))).containsKey("b");
    assertThat(asMap(result.get("after"))).containsEntry("b", 2);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }
}
