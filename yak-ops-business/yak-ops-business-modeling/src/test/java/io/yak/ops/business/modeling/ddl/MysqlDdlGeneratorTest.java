package io.yak.ops.business.modeling.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MySQL DDL rendering: columns, defaults, keys, indexes, partitions, options. */
class MysqlDdlGeneratorTest {

  private final MysqlDdlGenerator generator = new MysqlDdlGenerator();

  private ColumnDefinition column(String name, String type, Integer length, Integer scale, boolean nullable, String defaultValue, String comment) {
    return new ColumnDefinition(null, name, type, length, scale, nullable, defaultValue, comment, null, 0);
  }

  @Test
  void rendersColumnsCommentsDefaultsAndTableComment() {
    String script = generator.generate(
        new DdlModel(
            "dim_user",
            " 用户维度表 ",
            List.of(
                column("user_id", "BIGINT", null, null, false, null, "主键"),
                column("name", "VARCHAR", 128, null, true, "未知", "姓名"),
                column("amount", "DECIMAL", 10, 2, true, "0.00", null),
                column("updated_at", "DATETIME", null, null, false, "CURRENT_TIMESTAMP", null)),
            List.of("user_id"),
            List.of(new IndexDefinition(null, "idx_name", true, "btree", List.of("name"))),
            "",
            List.of(),
            "",
            Map.of("ENGINE", "InnoDB", "CHARSET", "utf8mb4")));

    assertThat(script)
        .contains("CREATE TABLE `dim_user` (")
        .contains("`user_id` BIGINT NOT NULL COMMENT '主键'")
        .contains("`name` VARCHAR(128) DEFAULT '未知' COMMENT '姓名'")
        .contains("`amount` DECIMAL(10,2) DEFAULT 0.00")
        .contains("`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP")
        .contains("PRIMARY KEY (`user_id`),")
        .contains("UNIQUE INDEX `idx_name` (`name`) USING BTREE")
        .contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=' 用户维度表 '")
        .endsWith(";\n");
    // 主键后无索引行时不应有多余逗号（本例有索引行,主键行应有逗号）
    assertThat(script).contains("PRIMARY KEY (`user_id`),\n  UNIQUE INDEX");
  }

  @Test
  void rendersPrimaryKeyWithoutTrailingCommaWhenNoIndexes() {
    String script = generator.generate(
        new DdlModel(
            "t1",
            null,
            List.of(column("id", "BIGINT", null, null, false, null, null)),
            List.of("id"),
            List.of(),
            "",
            List.of(),
            "",
            Map.of()));

    assertThat(script)
        .contains("PRIMARY KEY (`id`)\n) ")
        .doesNotContain("PRIMARY KEY (`id`),");
  }

  @Test
  void rendersHashPartitionAndEscapesQuotes() {
    String script = generator.generate(
        new DdlModel(
            "t2",
            "它's",
            List.of(column("dt", "DATE", null, null, false, null, null)),
            List.of(),
            List.of(),
            "HASH",
            List.of("dt"),
            "",
            Map.of()));

    assertThat(script).contains("PARTITION BY HASH (`dt`);");
    assertThat(script).contains("COMMENT='它''s'");
  }

  @Test
  void rendersRangePartitionWithDefinitionHint() {
    String script = generator.generate(
        new DdlModel(
            "t3",
            null,
            List.of(column("dt", "DATE", null, null, false, null, null)),
            List.of(),
            List.of(),
            "RANGE",
            List.of("dt"),
            "YEAR(dt)",
            Map.of()));

    assertThat(script).contains("PARTITION BY RANGE (YEAR(dt))");
    assertThat(script).contains("补充分区定义");
  }

  @Test
  void skipsUnknownPartitionType() {
    String script = generator.generate(
        new DdlModel(
            "t4",
            null,
            List.of(column("id", "INT", null, null, false, null, null)),
            List.of(),
            List.of(),
            "XYZ",
            List.of(),
            "",
            Map.of()));

    assertThat(script).contains("分区类型 XYZ 在 MySQL 模板中未支持");
    assertThat(script).doesNotContain("PARTITION BY");
  }
}
