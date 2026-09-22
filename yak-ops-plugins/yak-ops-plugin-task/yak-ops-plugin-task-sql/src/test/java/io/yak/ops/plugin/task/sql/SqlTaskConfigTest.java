package io.yak.ops.plugin.task.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.spi.task.model.SqlDialect;
import org.junit.jupiter.api.Test;

class SqlTaskConfigTest {

  @Test
  void legacyConfigUsesGenericDialect() {
    SqlTaskConfig config = SqlTaskConfig.parse("{\"dataSourceId\":\"42\"}");

    assertThat(config.dialect()).isEqualTo(SqlDialect.GENERIC);
  }

  @Test
  void acceptsCanonicalAndCompatibilityDialectAliases() {
    assertThat(SqlTaskConfig.parse("{\"dialect\":\"MYSQL\"}").dialect())
        .isEqualTo(SqlDialect.MYSQL);
    assertThat(SqlTaskConfig.parse("{\"dbType\":\"PostgreSQL\"}").dialect())
        .isEqualTo(SqlDialect.POSTGRE_SQL);
    assertThat(SqlTaskConfig.parse("{\"databaseType\":\"StarRocks\"}").dialect())
        .isEqualTo(SqlDialect.STARROCKS);
  }

  @Test
  void rejectsUnsupportedExplicitDialect() {
    assertThatThrownBy(() -> SqlTaskConfig.parse("{\"dialect\":\"MONGODB\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL dialect");
  }
}
