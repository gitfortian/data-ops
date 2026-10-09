package io.yak.framework.security.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityUtilityCompatibilityTest {
  @Test
  void keepsJdbcScalarLongConversionAndFailClosedOnNonNumbers() {
    assertThat(DatabaseNumberUtils.toLong(null)).isNull();
    assertThat(DatabaseNumberUtils.toLong(new BigDecimal("14"))).isEqualTo(14L);
    assertThatThrownBy(() -> DatabaseNumberUtils.toLong("14"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("java.lang.String");
  }

  @Test
  void keepsJsonObjectAndListContracts() {
    String json = JsonUtils.toJson(Map.of("role", "admin"));
    assertThat(JsonUtils.fromJson(json, Map.class).get("role")).isEqualTo("admin");
    assertThat(JsonUtils.toList("[\"one\",\"two\"]", String.class))
        .containsExactly("one", "two");
    assertThatThrownBy(() -> JsonUtils.fromJson("{invalid-json", Map.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preservesIntersectionAndLegacyInvalidRandomLength() {
    assertThat(MathUtil.getIntersection(List.of(1L, 2L), List.of(2L, 3L)))
        .isEqualTo(Set.of(2L));
    assertThat(MathUtil.getIntersection(List.of(), List.of(1L))).isEmpty();
    assertThat(MathUtil.getRandomNumber(0)).isZero();
    assertThat(MathUtil.getRandomNumber(19)).isZero();
    assertThat(MathUtil.getRandomNumber(1)).isBetween(1L, 9L);
  }
}
