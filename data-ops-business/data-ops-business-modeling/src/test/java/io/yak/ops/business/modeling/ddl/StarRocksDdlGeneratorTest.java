package io.yak.ops.business.modeling.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** StarRocks 与 Doris 同族，只差主键模型写法，因此只测差异点。 */
class StarRocksDdlGeneratorTest {

  private final StarRocksDdlGenerator generator = new StarRocksDdlGenerator();

  private ColumnDefinition column(
      String name, String type, Integer length, Integer scale, boolean nullable, String comment) {
    return new ColumnDefinition(
        null, name, type, length, scale, nullable, null, comment, null, 0);
  }

  @Test
  void usesPrimaryKeyClauseAndNativeJson() {
    String script = generator.generate(new DdlModel(
        "dim_user", "用户维度表",
        List.of(
            column("user_id", "BIGINT", null, null, false, "主键"),
            column("payload", "JSON", null, null, true, null)),
        List.of("user_id"), List.of(), "", List.of(), "", Map.of()));

    assertThat(script)
        .contains("PRIMARY KEY(`user_id`)")
        .doesNotContain("UNIQUE KEY")
        .contains("`payload` JSON")
        .contains("DISTRIBUTED BY HASH(`user_id`)")
        .contains("COMMENT '用户维度表'");
  }

  @Test
  void reportsDialectNameInDegradationHints() {
    String script = generator.generate(new DdlModel(
        "t1", null,
        List.of(column("id", "INT", null, null, false, null)),
        List.of("id"),
        List.of(new IndexDefinition(null, "idx_id", false, "btree", List.of("id"))),
        "", List.of(), "", Map.of("replication_num", "3")));

    assertThat(script)
        .contains("--   二级索引 idx_id 在 STARROCKS 建表语句内无对应写法，已跳过")
        .contains("--   表属性 replication_num 在 STARROCKS 无对应写法，已忽略，请按目标库补充");
  }
}
