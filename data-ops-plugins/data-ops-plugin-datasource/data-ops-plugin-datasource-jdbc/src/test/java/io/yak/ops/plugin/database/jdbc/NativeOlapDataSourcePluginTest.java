package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.plugin.database.jdbc.clickhouse.ClickHouseDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.starrocks.StarRocksDataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourceConnection;
import org.junit.jupiter.api.Test;

class NativeOlapDataSourcePluginTest {

  @Test
  void starRocksKeepsManagementJdbcSeparateFromNativeFeNodes() {
    StarRocksDataSourcePlugin plugin = new StarRocksDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"STARROCKS\",\"host\":\"sr-fe\",\"port\":9030,"
                + "\"database\":\"analytics\",\"username\":\"root\",\"password\":\"secret\","
                + "\"nodeUrls\":\"sr-fe:8030,sr-fe-2:8030\"}");

    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://sr-fe:9030/analytics");
    assertThat(connection.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(connection.normalizedJson()).contains("\"nodeUrls\":\"sr-fe:8030,sr-fe-2:8030\"");
  }

  @Test
  void clickHouseUsesNativeCompatibleHttpJdbcManagementEndpoint() {
    ClickHouseDataSourcePlugin plugin = new ClickHouseDataSourcePlugin();
    DataSourceConnection connection =
        plugin.parseConnection(
            "{\"dbType\":\"CLICKHOUSE\",\"host\":\"clickhouse\",\"port\":8123,"
                + "\"database\":\"analytics\",\"username\":\"default\",\"password\":\"secret\","
                + "\"serverTimeZone\":\"Asia/Shanghai\"}");

    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:clickhouse://clickhouse:8123/analytics");
    assertThat(connection.driverClassName()).isEqualTo("com.clickhouse.jdbc.ClickHouseDriver");
    assertThat(connection.normalizedJson()).contains("\"serverTimeZone\":\"Asia/Shanghai\"");
  }
}
