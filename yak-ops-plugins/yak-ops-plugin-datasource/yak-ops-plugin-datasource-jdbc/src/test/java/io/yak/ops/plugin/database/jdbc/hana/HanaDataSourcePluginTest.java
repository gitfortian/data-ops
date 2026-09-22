package io.yak.ops.plugin.database.jdbc.hana;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import org.junit.jupiter.api.Test;

class HanaDataSourcePluginTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldUseNgdbcAndBuildHanaDatabaseUrl() throws Exception {
    HanaDataSourcePlugin plugin = new HanaDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"HANA\",\"host\":\"127.0.0.1\","
                + "\"database\":\"HXE\",\"schema\":\"SALES\","
                + "\"username\":\"SYSTEM\"}");

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.HANA);
    assertThat(connection.jdbcUrl())
        .isEqualTo("jdbc:sap://127.0.0.1:30015/?databaseName=HXE");
    assertThat(connection.driverClassName()).isEqualTo("com.sap.db.jdbc.Driver");
    assertThat(plugin.acceptsUrl(connection.jdbcUrl())).isTrue();

    JsonNode normalized = MAPPER.readTree(connection.normalizedJson());
    assertThat(normalized.path("database").asText()).isEqualTo("HXE");
    assertThat(normalized.path("schema").asText()).isEqualTo("SALES");
    assertThat(normalized.path("dialect").asText()).isEqualTo("hana");
  }

  @Test
  void shouldInferDatabaseNameFromExplicitSapJdbcUrl() throws Exception {
    HanaDataSourcePlugin plugin = new HanaDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"SAP-HANA\","
                + "\"jdbcUrl\":\"jdbc:sap://hana:30013/?databaseName=TENANT%5F01&encrypt=true\","
                + "\"username\":\"SYSTEM\"}");

    JsonNode normalized = MAPPER.readTree(connection.normalizedJson());
    assertThat(normalized.path("database").asText()).isEqualTo("TENANT_01");
    assertThat(normalized.path("dialect").asText()).isEqualTo("hana");
  }

  @Test
  void shouldExposeOfflineControlPlaneCapabilities() {
    HanaDataSourcePlugin plugin = new HanaDataSourcePlugin();

    assertThat(plugin.descriptor().displayName()).isEqualTo("SAP HANA");
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
        .isEqualTo("jdbc:sap://{host}:{port}/?databaseName={database}");
  }
}
