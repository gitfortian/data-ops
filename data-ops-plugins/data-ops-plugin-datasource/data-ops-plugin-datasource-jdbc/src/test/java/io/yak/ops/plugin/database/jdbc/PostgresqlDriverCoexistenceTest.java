package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Driver;
import org.junit.jupiter.api.Test;

class PostgresqlDriverCoexistenceTest {
  @Test
  void postgresAndOpenGaussHaveSeparateDriverNamespaces() throws Exception {
    Driver postgres = (Driver) Class.forName("org.postgresql.Driver").getConstructor().newInstance();
    Driver openGauss = (Driver) Class.forName("org.opengauss.Driver").getConstructor().newInstance();
    assertThat(postgres.acceptsURL("jdbc:postgresql://localhost/data_ops")).isTrue();
    assertThat(postgres.acceptsURL("jdbc:opengauss://localhost/data_ops")).isFalse();
    assertThat(openGauss.acceptsURL("jdbc:opengauss://localhost/data_ops")).isTrue();
    assertThat(postgres.getClass().getProtectionDomain().getCodeSource().getLocation().toString())
        .contains("postgresql-").doesNotContain("opengauss-jdbc");
  }
}
