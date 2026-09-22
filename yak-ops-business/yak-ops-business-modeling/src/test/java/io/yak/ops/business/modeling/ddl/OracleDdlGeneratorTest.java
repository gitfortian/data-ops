package io.yak.ops.business.modeling.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Oracle rendering: inline column comments, trailing COMMENT ON TABLE / CREATE INDEX. */
class OracleDdlGeneratorTest {

  private final OracleDdlGenerator generator = new OracleDdlGenerator();

  private ColumnDefinition column(
      String name, String type, Integer length, Integer scale, boolean nullable,
      String defaultValue, String comment) {
    return new ColumnDefinition(
        null, name, type, length, scale, nullable, defaultValue, comment, null, 0);
  }

  @Test
  void rendersColumnsInlineCommentsAndPrimaryKey() {
    String script = generator.generate(new DdlModel(
        "DIM_USER", "用户维度表",
        List.of(
            column("USER_ID", "NUMBER", 10, null, false, null, "主键"),
            column("NAME", "VARCHAR2", 128, null, true, "未知", "姓名"),
            column("SCORE", "NUMBER", 10, 2, true, "0", null)),
        List.of("USER_ID"),
        List.of(new IndexDefinition(null, "IDX_NAME", true, "btree", List.of("NAME"))),
        "", List.of(), "", Map.of()));

    assertThat(script)
        .contains("CREATE TABLE \"DIM_USER\" (")
        .contains("\"USER_ID\" NUMBER(10) NOT NULL COMMENT '主键'")
        .contains("\"NAME\" VARCHAR2(128) DEFAULT '未知' COMMENT '姓名'")
        .contains("\"SCORE\" NUMBER(10,2) DEFAULT 0")
        .contains("PRIMARY KEY (\"USER_ID\")\n);")
        .contains("CREATE UNIQUE INDEX \"IDX_NAME\" ON \"DIM_USER\" (\"NAME\");")
        .contains("COMMENT ON TABLE \"DIM_USER\" IS '用户维度表';")
        // Oracle 没有 USING 写法
        .doesNotContain("USING");
  }

  @Test
  void mapsMysqlTypesThroughPreferenceChain() {
    String script = generator.generate(new DdlModel(
        "T_MAP", null,
        List.of(
            column("ID", "BIGINT", null, null, false, null, null),
            column("CODE", "VARCHAR", 64, null, true, null, null),
            column("AT", "DATETIME", null, null, true, null, null),
            column("RATIO", "DOUBLE", null, null, true, null, null)),
        List.of(), List.of(), "", List.of(), "", Map.of()));

    assertThat(script)
        .contains("\"ID\" NUMBER")
        .contains("\"CODE\" VARCHAR2(64)")
        .contains("\"AT\" TIMESTAMP")
        .contains("\"RATIO\" BINARY_DOUBLE")
        .contains("--   类型 BIGINT 在 ORACLE 不支持，已按 NUMBER 输出")
        .contains("--   类型 VARCHAR 在 ORACLE 不支持，已按 VARCHAR2 输出");
  }

  @Test
  void keepsCaseSensitiveQuotedIdentifiersAsHint() {
    String script = generator.generate(new DdlModel(
        "dim_user", null,
        List.of(column("USER_ID", "NUMBER", null, null, false, null, null)),
        List.of("USER_ID"), List.of(), "", List.of(), "", Map.of()));

    assertThat(script)
        .contains("--   Oracle 双引号标识符区分大小写");
  }

  @Test
  void rangePartitionStaysInsideStatementWithDefinitionHint() {
    String script = generator.generate(new DdlModel(
        "T_PART", null,
        List.of(column("DT", "DATE", null, null, false, null, null)),
        List.of(), List.of(), "range", List.of("DT"), "", Map.of()));

    assertThat(script)
        .contains(") PARTITION BY RANGE (\"DT\");")
        .contains("PARTITION p1 VALUES LESS THAN (...)");
  }

  @Test
  void unknownPartitionTypeAndTablePropertiesOnlyHint() {
    String script = generator.generate(new DdlModel(
        "T_SKIP", null,
        List.of(column("ID", "NUMBER", null, null, false, null, null)),
        List.of("ID"), List.of(), "HASH", List.of("ID"), "", Map.of("TABLESPACE", "USERS")));

    assertThat(script)
        .doesNotContain("PARTITION BY")
        .contains("--   分区类型 HASH 在 Oracle 模板中未支持，已跳过")
        .contains("--   表属性 TABLESPACE 在 ORACLE 无对应写法，已忽略，请按目标库补充");
  }
}
