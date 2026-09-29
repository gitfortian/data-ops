package io.yak.ops.business.modeling.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PostgreSQL rendering: double-quoted identifiers, trailing COMMENT ON / CREATE INDEX. */
class PostgreSqlDdlGeneratorTest {

  private final PostgreSqlDdlGenerator generator = new PostgreSqlDdlGenerator();

  private ColumnDefinition column(
      String name, String type, Integer length, Integer scale, boolean nullable,
      String defaultValue, String comment) {
    return new ColumnDefinition(
        null, name, type, length, scale, nullable, defaultValue, comment, null, 0);
  }

  private DdlModel singleColumnModel(List<ColumnDefinition> columns) {
    return new DdlModel(
        "dim_user", "用户维度表", columns, List.of("user_id"), List.of(), "", List.of(), "",
        Map.of());
  }

  @Test
  void quotesIdentifiersAndKeepsSavedTypes() {
    String script = generator.generate(singleColumnModel(List.of(
        column("user_id", "BIGINT", null, null, false, null, "主键"),
        column("name", "VARCHAR", 128, null, true, "未知", "姓名"),
        column("amount", "NUMERIC", 10, 2, true, "0.00", null),
        column("updated_at", "TIMESTAMP", null, null, false, "CURRENT_TIMESTAMP", null))));

    assertThat(script)
        .contains("CREATE TABLE \"dim_user\" (")
        .contains("\"user_id\" BIGINT NOT NULL")
        .contains("\"name\" VARCHAR(128) DEFAULT '未知'")
        .contains("\"amount\" NUMERIC(10,2) DEFAULT 0.00")
        .contains("\"updated_at\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
        .contains("PRIMARY KEY (\"user_id\")\n);")
        // 注释不内联，全部拆成表后语句
        .doesNotContain("COMMENT '")
        .contains("COMMENT ON TABLE \"dim_user\" IS '用户维度表';")
        .contains("COMMENT ON COLUMN \"dim_user\".\"user_id\" IS '主键';")
        .contains("COMMENT ON COLUMN \"dim_user\".\"name\" IS '姓名';")
        .doesNotContain("COMMENT ON COLUMN \"dim_user\".\"amount\"");
    assertThat(script).endsWith(";\n");
  }

  @Test
  void rendersStandaloneIndexesWithAccessMethod() {
    String script = generator.generate(new DdlModel(
        "t1", null,
        List.of(column("id", "INTEGER", null, null, false, null, null)),
        List.of(),
        List.of(new IndexDefinition(null, "idx_id", true, "btree", List.of("id"))),
        "", List.of(), "", Map.of()));

    assertThat(script)
        .contains("CREATE UNIQUE INDEX \"idx_id\" ON \"t1\" USING btree (\"id\");");
  }

  @Test
  void putsRangePartitionInsideCreateTableAndHintsChildPartitions() {
    String script = generator.generate(new DdlModel(
        "t2", null,
        List.of(column("dt", "DATE", null, null, false, null, null)),
        List.of(), List.of(), "range", List.of("dt"), "", Map.of()));

    assertThat(script)
        .contains(") PARTITION BY RANGE (\"dt\");")
        .contains("--   分区表需在执行前补充子分区：CREATE TABLE t2_p1 PARTITION OF t2 FOR VALUES ...");
  }

  @Test
  void skipsUnsupportedPartitionTypeWithHint() {
    String script = generator.generate(new DdlModel(
        "t3", null,
        List.of(column("dt", "DATE", null, null, false, null, null)),
        List.of(), List.of(), "HASH", List.of("dt"), "", Map.of()));

    assertThat(script)
        .doesNotContain("PARTITION BY")
        .contains("--   分区类型 HASH 在 PostgreSQL 模板中未支持，已跳过");
  }

  @Test
  void resolvesForeignTypesThroughPreferenceChainAndHints() {
    String script = generator.generate(singleColumnModel(List.of(
        column("code", "STRING", null, null, true, null, null),
        column("flag", "BOOLEAN", null, null, false, null, null),
        column("payload", "JSON", null, null, true, null, null))));

    assertThat(script)
        .contains("\"code\" VARCHAR")
        .contains("\"payload\" JSONB")
        .contains("--   类型 STRING 在 POSTGRESQL 不支持，已按 VARCHAR 输出")
        .doesNotContain("--   类型 BOOLEAN");
    assertThat(script).doesNotContain("BIGINT(20)");
  }

  @Test
  void warnsWhenPrimaryKeyDoesNotCoverPartitionKey() {
    String script = generator.generate(new DdlModel(
        "t5", null,
        List.of(
            column("id", "BIGINT", null, null, false, null, null),
            column("dt", "DATE", null, null, false, null, null)),
        List.of("id"), List.of(), "RANGE", List.of("dt"), "", Map.of()));

    assertThat(script)
        .contains("--   PostgreSQL 要求分区键包含在主键内，主键未包含 dt");
  }

  @Test
  void partitionedTableWithoutPrimaryKeyIsNotFlagged() {
    String script = generator.generate(new DdlModel(
        "t6", null,
        List.of(column("dt", "DATE", null, null, false, null, null)),
        List.of(), List.of(), "RANGE", List.of("dt"), "", Map.of()));

    assertThat(script).doesNotContain("要求分区键包含在主键内");
  }
}
