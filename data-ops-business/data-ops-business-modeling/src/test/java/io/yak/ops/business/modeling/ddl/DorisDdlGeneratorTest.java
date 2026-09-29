package io.yak.ops.business.modeling.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Doris rendering: UNIQUE KEY model, trailing COMMENT / PARTITION / DISTRIBUTED clauses. */
class DorisDdlGeneratorTest {

  private final DorisDdlGenerator generator = new DorisDdlGenerator();

  private ColumnDefinition column(
      String name, String type, Integer length, Integer scale, boolean nullable,
      String defaultValue, String comment) {
    return new ColumnDefinition(
        null, name, type, length, scale, nullable, defaultValue, comment, null, 0);
  }

  private DdlModel model(
      List<ColumnDefinition> columns, List<String> primaryKey, List<IndexDefinition> indexes,
      String partitionType, List<String> partitionColumns, Map<String, String> properties) {
    return new DdlModel(
        "dim_user", "用户维度表", columns, primaryKey, indexes, partitionType, partitionColumns, "",
        properties);
  }

  @Test
  void rendersKeyModelCommentAndDistributionInOrder() {
    String script = generator.generate(model(
        List.of(
            column("user_id", "BIGINT", null, null, false, null, "主键"),
            column("name", "VARCHAR", 128, null, true, "未知", "姓名")),
        List.of("user_id"), List.of(), "", List.of(), Map.of()));

    assertThat(script)
        .contains("CREATE TABLE `dim_user` (")
        .contains("`user_id` BIGINT NOT NULL COMMENT '主键'")
        .contains("`name` VARCHAR(128) DEFAULT '未知' COMMENT '姓名'")
        .contains("UNIQUE KEY(`user_id`)")
        .contains("COMMENT '用户维度表'")
        .contains("DISTRIBUTED BY HASH(`user_id`)")
        .doesNotContain("PRIMARY KEY (");
    assertThat(script.indexOf("UNIQUE KEY")).isLessThan(script.indexOf("COMMENT '用户维度表'"));
    assertThat(script.indexOf("COMMENT '用户维度表'")).isLessThan(script.indexOf("DISTRIBUTED BY"));
    assertThat(script).contains("--   分桶数与副本数请按集群补充");
  }

  @Test
  void fallsBackToFirstColumnAsBucketKeyWithoutPrimaryKey() {
    String script = generator.generate(model(
        List.of(column("dt", "DATE", null, null, false, null, null)),
        List.of(), List.of(), "", List.of(), Map.of()));

    assertThat(script)
        .doesNotContain("KEY(")
        .contains("DISTRIBUTED BY HASH(`dt`)");
  }

  @Test
  void degradesSecondaryIndexesToComments() {
    String script = generator.generate(model(
        List.of(column("user_id", "BIGINT", null, null, false, null, null)),
        List.of("user_id"),
        List.of(new IndexDefinition(null, "idx_user", true, "btree", List.of("user_id"))),
        "", List.of(), Map.of()));

    assertThat(script)
        .doesNotContain("CREATE INDEX")
        .contains("--   二级索引 idx_user 在 DORIS 建表语句内无对应写法，已跳过");
  }

  @Test
  void rangePartitionComesBeforeDistribution() {
    String script = generator.generate(model(
        List.of(column("dt", "DATE", null, null, false, null, null)),
        List.of(), List.of(), "RANGE", List.of("dt"), Map.of()));

    assertThat(script)
        .contains("PARTITION BY RANGE (`dt`)\nDISTRIBUTED BY HASH(`dt`)")
        .contains("补充分区定义（PARTITION p1 VALUES LESS THAN (...)）");
  }

  @Test
  void hashPartitionIsLeftToDistributionClause() {
    String script = generator.generate(model(
        List.of(column("id", "INT", null, null, false, null, null)),
        List.of("id"), List.of(), "HASH", List.of("id"), Map.of()));

    assertThat(script)
        .doesNotContain("PARTITION BY")
        .contains("--   按 HASH 的哈希分列在 DORIS 里由 DISTRIBUTED BY 表达，未重复生成");
  }

  @Test
  void warnsWhenPrimaryKeyColumnsAreNotLeading() {
    String script = generator.generate(model(
        List.of(
            column("name", "VARCHAR", 32, null, true, null, null),
            column("user_id", "BIGINT", null, null, false, null, null)),
        List.of("user_id"), List.of(), "", List.of(), Map.of()));

    assertThat(script).contains("--   UNIQUE KEY 要求主键列位于列定义最前，请按 user_id 的顺序调整列位置");
  }

  @Test
  void mapsForeignTypesAndIgnoresForeignProperties() {
    String script = generator.generate(model(
        List.of(
            column("payload", "JSON", null, null, true, null, null),
            column("label", "TEXT", null, null, true, null, null)),
        List.of(), List.of(), "", List.of(), Map.of("ENGINE", "OLAP")));

    assertThat(script)
        .contains("`payload` JSONB")
        .contains("`label` STRING")
        .contains("--   类型 JSON 在 DORIS 不支持，已按 JSONB 输出")
        .contains("--   类型 TEXT 在 DORIS 不支持，已按 STRING 输出")
        .contains("--   表属性 ENGINE 在 DORIS 无对应写法，已忽略，请按目标库补充");
  }
}
