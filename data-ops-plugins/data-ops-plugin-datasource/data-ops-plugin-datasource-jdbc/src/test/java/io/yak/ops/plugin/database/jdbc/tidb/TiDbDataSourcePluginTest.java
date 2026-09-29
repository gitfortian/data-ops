package io.yak.ops.plugin.database.jdbc.tidb;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import org.junit.jupiter.api.Test;

class TiDbDataSourcePluginTest {

  @Test
  void shouldUseMysqlConnectorJWithTiDbDefaults() {
    TiDbDataSourcePlugin plugin = new TiDbDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"TIDB\",\"host\":\"127.0.0.1\","
                + "\"database\":\"demo\",\"username\":\"root\"}");

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.TIDB);
    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:4000/demo");
    assertThat(connection.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(plugin.acceptsUrl(connection.jdbcUrl())).isTrue();
  }

  @Test
  void shouldExposeOfflineControlPlaneCapabilities() {
    TiDbDataSourcePlugin plugin = new TiDbDataSourcePlugin();

    assertThat(plugin.descriptor().displayName()).isEqualTo("TiDB");
    assertThat(plugin.descriptor().capabilities())
        .contains(
            DataSourceCapability.CONNECTION_TEST,
            DataSourceCapability.CATALOG_METADATA,
            DataSourceCapability.CATALOG_READ,
            DataSourceCapability.SQL_EXECUTION,
            DataSourceCapability.TRANSACTIONS,
            DataSourceCapability.SSH_TUNNEL);
    assertThat(plugin.descriptor().connectionForm().sections().get(0).fields().stream()
            .filter(field -> "jdbcUrl".equals(field.key()))
            .findFirst()
            .orElseThrow()
            .jdbcUrlLinkage()
            .template())
        .isEqualTo("jdbc:mysql://{host}:{port}/{database}");
  }
}
