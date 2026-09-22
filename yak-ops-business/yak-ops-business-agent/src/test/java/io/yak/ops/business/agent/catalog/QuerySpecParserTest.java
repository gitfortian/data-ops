package io.yak.ops.business.agent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.agent.domain.DatasetQuerySpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 查询规格 mini 语法解析边界测试：重点保护值中冒号不被截断（评审 P1-2）。 */
class QuerySpecParserTest {

  private final QuerySpecParser parser = new QuerySpecParser();

  @Test
  void filterValueContainingColonIsPreserved() {
    List<DatasetQuerySpec.Filter> filters =
        parser.parseFilters(List.of("create_time:GTE:2026-08-01T12:30"));

    assertEquals(1, filters.size());
    assertEquals("2026-08-01T12:30", filters.get(0).value());
  }

  @Test
  void betweenRequiresPipeSeparatedValues() {
    List<DatasetQuerySpec.Filter> filters =
        parser.parseFilters(List.of("day:BETWEEN:2026-08-01|2026-08-07"));

    assertEquals(2, filters.get(0).values().size());
    assertEquals(null, filters.get(0).value());
  }

  @Test
  void inWithSingleValueIsRejected() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class, () -> parser.parseFilters(List.of("tag:IN:a")));
    assertTrue(error.getMessage().contains("至少两个值"));
  }

  @Test
  void isNullNeedsNoValue() {
    List<DatasetQuerySpec.Filter> filters = parser.parseFilters(List.of("deleted_at:IS_NULL"));

    assertEquals(DatasetQuerySpec.Operator.IS_NULL, filters.get(0).operator());
    assertEquals(null, filters.get(0).value());
  }

  @Test
  void missingValueIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parseFilters(List.of("status:EQ")));
  }

  @Test
  void unknownOperatorIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parseFilters(List.of("status:MAYBE:x")),
        "非法过滤操作符");
  }

  @Test
  void metricAggregationCaseInsensitive() {
    List<DatasetQuerySpec.Metric> metrics = parser.parseMetrics(List.of("amount:sum"));

    assertEquals(DatasetQuerySpec.Aggregation.SUM, metrics.get(0).aggregation());
  }

  @Test
  void invalidAggregationIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parseMetrics(List.of("amount:MEDIAN")));
  }

  @Test
  void sortDirectionIsValidated() {
    assertEquals(1, parser.parseSorts(List.of("day:DESC")).size());
    assertThrows(IllegalArgumentException.class, () -> parser.parseSorts(List.of("day:UP")));
  }
}
