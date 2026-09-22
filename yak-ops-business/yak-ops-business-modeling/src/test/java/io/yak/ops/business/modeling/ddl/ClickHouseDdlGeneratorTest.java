package io.yak.ops.business.modeling.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ClickHouse rendering: Nullable typing, engine clause ordering, degraded indexes. */
class ClickHouseDdlGeneratorTest {

  private final ClickHouseDdlGenerator generator = new ClickHouseDdlGenerator();

  private ColumnDefinition column(
      String name, String type, Integer length, Integer scale, boolean nullable,
      String defaultValue, String comment) {
    return new ColumnDefinition(
        null, name, type, length, scale, nullable, defaultValue, comment, null, 0);
  }

  private DdlModel model(
      List<ColumnDefinition> columns, List<String> primaryKey, String partitionType,
      List<String> partitionColumns, String partitionExpression) {
    return new DdlModel(
        "t_ck", "ClickHouse 表", columns, primaryKey, List.of(), partitionType, partitionColumns,
        partitionExpression, Map.of());
  }

  @Test
  void wrapsNullableColumnsAndPutsEngineClauseLast() {
    String script = generator.generate(model(
        List.of(
            column("user_id", "BIGINT", null, null, false, null, "主键"),
            column("name", "VARCHAR", 128, null, true, "未知", "姓名"),
            column("amount", "DECIMAL", 10, 2, true, null, null),
            column("flag", "INT", null, null, false, "0", null)),
        List.of("user_id"), "", List.of(), ""));

    assertThat(script)
        .contains("CREATE TABLE `t_ck` (")
        .contains("`user_id` INT64 COMMENT '主键'")
        .contains("`name` Nullable(STRING) DEFAULT '未知' COMMENT '姓名'")
        .contains("`amount` Nullable(DECIMAL(10,2))")
        .contains("`flag` INT32 DEFAULT 0")
        .doesNotContain("NOT NULL")
        .contains(")\nENGINE = MergeTree\n")
        .contains("ORDER BY (`user_id`)\n")
        .contains("COMMENT 'ClickHouse 表'\n;\n");
    assertThat(script.indexOf("ENGINE = MergeTree"))
        .isLessThan(script.indexOf("ORDER BY (`user_id`)"));
    assertThat(script.indexOf("ORDER BY (`user_id`)"))
        .isLessThan(script.indexOf("COMMENT 'ClickHouse 表'"));
  }

  @Test
  void keepsNonNullableTypingForSortKeyWithoutPrimaryKey() {
    String script = generator.generate(model(
        List.of(
            column("event_time", "DATETIME", null, null, true, null, null),
            column("payload", "STRING", null, null, true, null, null)),
        List.of(), "", List.of(), ""));

    assertThat(script)
        .contains("`event_time` DATETIME")
        .contains("`payload` Nullable(STRING)")
        .contains("ORDER BY (`event_time`)")
        .contains("--   主键/排序键列 event_time 在 ClickHouse 不能为 Nullable，已按非空输出");
  }

  @Test
  void rendersPartitionExpressionBeforeOrderBy() {
    String script = generator.generate(model(
        List.of(column("dt", "DATE", null, null, false, null, null)),
        List.of("dt"), "RANGE", List.of(), "toYYYYMM(dt)"));

    assertThat(script)
        .contains("ENGINE = MergeTree\nPARTITION BY toYYYYMM(dt)\nORDER BY (`dt`)");
  }

  @Test
  void fallsBackToPartitionColumnsWithExpressionAdvice() {
    String script = generator.generate(model(
        List.of(column("dt", "DATE", null, null, false, null, null)),
        List.of("dt"), "RANGE", List.of("dt"), ""));

    assertThat(script)
        .contains("PARTITION BY `dt`")
        .contains("--   ClickHouse 分区键建议写成表达式（如 toYYYYMM(dt)）")
        .doesNotContain("分区键不接受 Nullable");
  }

  @Test
  void flagsNullablePartitionColumn() {
    String script = generator.generate(model(
        List.of(
            column("id", "INT64", null, null, false, null, null),
            column("dt", "DATE", null, null, true, null, null)),
        List.of("id"), "RANGE", List.of("dt"), ""));

    assertThat(script)
        .contains("--   分区列 dt 为可空列，ClickHouse 分区键不接受 Nullable");
  }

  @Test
  void degradesIndexesAndTableProperties() {
    String script = generator.generate(new DdlModel(
        "t_idx", null,
        List.of(column("id", "UUID", null, null, false, null, null)),
        List.of("id"),
        List.of(new IndexDefinition(null, "idx_id", false, "bloom_filter", List.of("id"))),
        "", List.of(), "", Map.of("TTL", "id + 1 DAY")));

    assertThat(script)
        .contains("`id` UUID")
        .doesNotContain("INDEX idx_id")
        .contains("--   二级索引 idx_id 需要 data-skipping index 表达式")
        .contains("--   表属性 TTL 在 CLICKHOUSE 无对应写法，已忽略，请按目标库补充");
  }

  @Test
  void hintsUnmappableTypesWithoutFailing() {
    String script = generator.generate(model(
        List.of(
            column("dt", "DATE", null, null, false, null, null),
            column("geo", "POINT", null, null, true, null, null)),
        List.of(), "", List.of(), ""));

    assertThat(script)
        .contains("`geo` Nullable(POINT)")
        .contains("--   类型 POINT 不在 CLICKHOUSE 类型目录中，请人工确认");
  }
}
