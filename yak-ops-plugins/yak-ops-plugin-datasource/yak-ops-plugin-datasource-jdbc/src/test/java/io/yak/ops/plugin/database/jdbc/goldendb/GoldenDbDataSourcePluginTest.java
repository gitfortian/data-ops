package io.yak.ops.plugin.database.jdbc.goldendb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import org.junit.jupiter.api.Test;

class GoldenDbDataSourcePluginTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldUseMysqlConnectorJWithGoldenDbIdentity() throws Exception {
    GoldenDbDataSourcePlugin plugin = new GoldenDbDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"GOLDENDB\",\"host\":\"127.0.0.1\","
                + "\"port\":1111,\"database\":\"demo\",\"username\":\"root\"}");

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.GOLDENDB);
    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:1111/demo");
    assertThat(connection.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(plugin.acceptsUrl(connection.jdbcUrl())).isTrue();

    JsonNode normalized = MAPPER.readTree(connection.normalizedJson());
    assertThat(normalized.path("dialect").asText()).isEqualTo("goldendb");
  }

  @Test
  void shouldKeepMysqlCompatibleDefaultPortEditable() {
    GoldenDbDataSourcePlugin plugin = new GoldenDbDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"golden-db\",\"host\":\"goldendb\","
                + "\"database\":\"app\",\"username\":\"root\"}");

    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://goldendb:3306/app");
  }

  @Test
  void shouldExposeOfflineControlPlaneCapabilities() {
    GoldenDbDataSourcePlugin plugin = new GoldenDbDataSourcePlugin();

    assertThat(plugin.descriptor().displayName()).isEqualTo("GoldenDB");
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
