package io.yak.ops.plugin.database.jdbc.gbase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceConnection;
import org.junit.jupiter.api.Test;

class GBaseDataSourcePluginTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldBuildGBase8aConnectionForLinkUpDialect() throws Exception {
    GBase8aDataSourcePlugin plugin = new GBase8aDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"GBASE8A\",\"host\":\"gbase8a\","
                + "\"database\":\"app\",\"username\":\"root\"}");

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.GBASE8A);
    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:gbase://gbase8a:5258/app");
    assertThat(connection.driverClassName()).isEqualTo("com.gbase.jdbc.Driver");
    assertThat(plugin.acceptsUrl(connection.jdbcUrl())).isTrue();
    assertThat(MAPPER.readTree(connection.normalizedJson()).path("dialect").asText())
        .isEqualTo("gbase8a");
  }

  @Test
  void shouldBuildGBase8cConnectionWithPublicSchemaFallback() throws Exception {
    GBase8cDataSourcePlugin plugin = new GBase8cDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"gbase-8c\",\"host\":\"gbase8c\","
                + "\"database\":\"app\",\"username\":\"root\"}");

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.GBASE8C);
    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:gbase8c://gbase8c:5432/app");
    assertThat(connection.driverClassName()).isEqualTo("com.gbase8c.Driver");
    JsonNode normalized = MAPPER.readTree(connection.normalizedJson());
    assertThat(normalized.path("dialect").asText()).isEqualTo("gbase8c");
    assertThat(normalized.path("schema").asText()).isEqualTo("public");
  }

  @Test
  void shouldBuildGBase8sConnectionWithServerAndOwnerSemantics() throws Exception {
    GBase8sDataSourcePlugin plugin = new GBase8sDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"GBASE8S\",\"host\":\"gbase8s\","
                + "\"database\":\"app\",\"username\":\"gbasedbt\","
                + "\"serverName\":\"gbase01\"}");

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.GBASE8S);
    assertThat(connection.jdbcUrl())
        .isEqualTo("jdbc:gbasedbt-sqli://gbase8s:9088/app:GBASEDBTSERVER=gbase01");
    assertThat(connection.driverClassName()).isEqualTo("com.gbasedbt.jdbc.Driver");
    JsonNode normalized = MAPPER.readTree(connection.normalizedJson());
    assertThat(normalized.path("dialect").asText()).isEqualTo("gbase8s");
    assertThat(normalized.path("serverName").asText()).isEqualTo("gbase01");
    assertThat(normalized.path("schema").asText()).isEqualTo("gbasedbt");
  }

  @Test
  void shouldInferGBase8sDatabaseAndServerFromExplicitJdbcUrl() throws Exception {
    GBase8sDataSourcePlugin plugin = new GBase8sDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"GBASE-8S\","
                + "\"jdbcUrl\":\"jdbc:gbasedbt-sqli://127.0.0.1:9088/archive:GBASEDBTSERVER=node1;DELIMIDENT=y\","
                + "\"username\":\"owner1\"}");

    assertThat(connection.database()).isEqualTo("archive");
    JsonNode normalized = MAPPER.readTree(connection.normalizedJson());
    assertThat(normalized.path("serverName").asText()).isEqualTo("node1");
    assertThat(normalized.path("schema").asText()).isEqualTo("owner1");
  }

  @Test
  void shouldAcceptGBase8sServerFromTextJdbcPropertiesForExplicitUrl() throws Exception {
    GBase8sDataSourcePlugin plugin = new GBase8sDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"GBASE8S\","
                + "\"jdbcUrl\":\"jdbc:gbasedbt-sqli://127.0.0.1:9088/archive\","
                + "\"username\":\"owner1\","
                + "\"properties\":\"{\\\"GBASEDBTSERVER\\\":\\\"node2\\\"}\"}");

    assertThat(connection.properties()).containsEntry("GBASEDBTSERVER", "node2");
    assertThat(MAPPER.readTree(connection.normalizedJson()).path("serverName").asText())
        .isEqualTo("node2");
  }

  @Test
  void shouldRejectGBase8sExplicitUrlWithoutEffectiveServerProperty() {
    GBase8sDataSourcePlugin plugin = new GBase8sDataSourcePlugin();

    assertThatThrownBy(
            () ->
                plugin.parseConnection(
                    "{\"dbType\":\"GBASE8S\","
                        + "\"jdbcUrl\":\"jdbc:gbasedbt-sqli://127.0.0.1:9088/archive\","
                        + "\"username\":\"owner1\",\"serverName\":\"standalone\"}"))
        .hasMessageContaining("GBASEDBTSERVER");
  }

  @Test
  void shouldKeepGenericJdbcUrlLinkageDisabledForGBase8s() {
    GBase8sDataSourcePlugin plugin = new GBase8sDataSourcePlugin();

    assertThat(
            plugin.descriptor().connectionForm().sections().stream()
                .flatMap(section -> section.fields().stream())
                .filter(field -> "jdbcUrl".equals(field.key()))
                .findFirst()
                .orElseThrow()
                .jdbcUrlLinkage())
        .isNull();
  }
}
