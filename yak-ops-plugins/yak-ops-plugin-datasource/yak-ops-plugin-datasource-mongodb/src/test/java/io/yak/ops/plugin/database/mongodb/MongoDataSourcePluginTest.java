package io.yak.ops.plugin.database.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import org.junit.jupiter.api.Test;

class MongoDataSourcePluginTest {

  private final MongoDataSourcePlugin plugin = new MongoDataSourcePlugin();

  @Test
  void normalizesStructuredConnectionWithoutDuplicatingCredentialsIntoDisplayUrl() {
    DataSourceConnection connection =
        plugin.parseConnection(
            """
            {
              "host":"mongo-a",
              "port":27018,
              "hosts":"mongo-a:27018,mongo-b:27018",
              "database":"business",
              "username":"yak user",
              "password":"s ecret",
              "authSource":"admin"
            }
            """);

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.MONGODB);
    assertThat(connection.jdbcUrl())
        .isEqualTo("mongodb://mongo-a:27018,mongo-b:27018/business?authSource=admin")
        .doesNotContain("yak", "secret");
    assertThat(connection.database()).isEqualTo("business");
    assertThat(connection.username()).isEqualTo("yak user");
    assertThat(connection.password()).isEqualTo("s ecret");
    assertThat(connection.normalizedJson())
        .contains("\"hosts\":\"mongo-a:27018,mongo-b:27018\"")
        .contains("\"password\":\"s ecret\"")
        .doesNotContain("\"uri\"");
  }

  @Test
  void exposesCatalogCapabilitiesAndPasswordAsSecretField() {
    assertThat(plugin.descriptor().capabilities())
        .containsExactlyInAnyOrder(
            DataSourceCapability.CONNECTION_TEST,
            DataSourceCapability.CATALOG_METADATA,
            DataSourceCapability.CATALOG_READ);
    assertThat(plugin.descriptor().secretFieldKeys()).containsExactly("password");
    assertThat(plugin.acceptsUrl("mongodb://localhost:27017/app")).isTrue();
    assertThat(plugin.acceptsUrl("mongodb+srv://cluster.example/app")).isTrue();
  }

  @Test
  void rejectsMalformedSeedHostAndPort() {
    assertThatThrownBy(
            () ->
                plugin.parseConnection(
                    "{\"host\":\"mongo-a\",\"port\":0,\"database\":\"app\"}"))
        .isInstanceOf(DataSourcePluginException.class)
        .hasMessageContaining("port");

    assertThatThrownBy(
            () ->
                plugin.parseConnection(
                    "{\"host\":\"mongo-a\",\"database\":\"app\","
                        + "\"hosts\":\"mongodb://mongo-a:27017\"}"))
        .isInstanceOf(DataSourcePluginException.class)
        .hasMessageContaining("Seed Host");
  }
}
