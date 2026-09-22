package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.duckdb.DuckDbDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.highgo.HighGoDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.iris.IrisDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.xugu.XuguDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.yashandb.YashanDbDataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import org.junit.jupiter.api.Test;

class SecondWaveJdbcDataSourcePluginTest {

  @Test
  void shouldBuildNetworkJdbcUrlsWithLinkUpAlignedDrivers() {
    assertNetwork(
        new YashanDbDataSourcePlugin(),
        DataSourceDbType.YASHAN_DB,
        "jdbc:yasdb://127.0.0.1:1688/demo",
        "com.yashandb.jdbc.Driver");
    assertNetwork(
        new HighGoDataSourcePlugin(),
        DataSourceDbType.HIGHGO,
        "jdbc:highgo://127.0.0.1:5866/demo",
        "com.highgo.jdbc.Driver");
    assertNetwork(
        new IrisDataSourcePlugin(),
        DataSourceDbType.IRIS,
        "jdbc:IRIS://127.0.0.1:1972/demo",
        "com.intersystems.jdbc.IRISDriver");
    assertNetwork(
        new XuguDataSourcePlugin(),
        DataSourceDbType.XUGU,
        "jdbc:xugu://127.0.0.1:5138/demo",
        "com.xugu.cloudjdbc.Driver");
  }

  @Test
  void shouldModelDuckDbAsFileBackedEmbeddedDatabase() {
    DuckDbDataSourcePlugin plugin = new DuckDbDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            """
            {
              "dbType":"DUCKDB",
              "databasePath":"/data/warehouse.duckdb",
              "schema":"main"
            }
            """);

    assertThat(connection.dbType()).isEqualTo(DataSourceDbType.DUCKDB);
    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:duckdb:/data/warehouse.duckdb");
    assertThat(connection.driverClassName()).isEqualTo("org.duckdb.DuckDBDriver");
    assertThat(connection.database()).isEqualTo("warehouse");
    assertThat(connection.schema()).isEqualTo("main");
    assertThat(connection.username()).isNull();
    assertThat(plugin.descriptor().capabilities())
        .contains(
            DataSourceCapability.CONNECTION_TEST,
            DataSourceCapability.CATALOG_METADATA,
            DataSourceCapability.CATALOG_READ,
            DataSourceCapability.SQL_EXECUTION,
            DataSourceCapability.TRANSACTIONS)
        .doesNotContain(DataSourceCapability.SSH_TUNNEL);
  }

  @Test
  void shouldRejectDuckDbMemoryAndRemoteCatalogUrls() {
    DuckDbDataSourcePlugin plugin = new DuckDbDataSourcePlugin();

    assertThatThrownBy(() -> plugin.parseConnection("{\"jdbcUrl\":\"jdbc:duckdb:\"}"))
        .hasMessageContaining("文件型 DuckDB");
    assertThatThrownBy(
            () -> plugin.parseConnection("{\"jdbcUrl\":\"jdbc:duckdb:memory:shared\"}"))
        .hasMessageContaining("内存数据库");
    assertThatThrownBy(
            () -> plugin.parseConnection("{\"jdbcUrl\":\"jdbc:duckdb:ducklake:demo\"}"))
        .hasMessageContaining("DuckLake");
    assertThatThrownBy(
            () ->
                plugin.parseConnection(
                    "{\"databasePath\":\"/data/demo.duckdb\","
                        + "\"properties\":{\"jdbc_instance_cache\":\"false\"}}"))
        .hasMessageContaining("jdbc_instance_cache=false");
  }

  private void assertNetwork(
      DataSourcePlugin plugin,
      DataSourceDbType dbType,
      String expectedUrl,
      String expectedDriver) {
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\""
                + dbType.name()
                + "\",\"host\":\"127.0.0.1\",\"database\":\"demo\","
                + "\"username\":\"tester\",\"password\":\"secret\"}");

    assertThat(connection.dbType()).isEqualTo(dbType);
    assertThat(connection.jdbcUrl()).isEqualTo(expectedUrl);
    assertThat(connection.driverClassName()).isEqualTo(expectedDriver);
    assertThat(connection.username()).isEqualTo("tester");
    assertThat(plugin.supports(DataSourceCapability.CONNECTION_TEST)).isTrue();
    assertThat(plugin.supports(DataSourceCapability.CATALOG_METADATA)).isTrue();
  }
}
