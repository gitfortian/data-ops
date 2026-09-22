package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.plugin.database.jdbc.db2.Db2DataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.oceanbase.OceanBaseDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.opengauss.OpenGaussDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.sqlserver.SqlServerDataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import org.junit.jupiter.api.Test;

class JdbcWaveOneDataSourcePluginTest {

  @Test
  void buildsLinkUpCompatibleUrlsAndDrivers() {
    assertConnection(
        new Db2DataSourcePlugin(),
        "DB2",
        "jdbc:db2://db.example:50000/app",
        "com.ibm.db2.jcc.DB2Driver");
    assertConnection(
        new OpenGaussDataSourcePlugin(),
        "OPEN_GAUSS",
        "jdbc:opengauss://db.example:5432/app",
        "org.opengauss.Driver");
    assertConnection(
        new SqlServerDataSourcePlugin(),
        "SQL_SERVER",
        "jdbc:sqlserver://db.example:1433;databaseName=app",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    assertConnection(
        new OceanBaseDataSourcePlugin(),
        "OCEANBASE",
        "jdbc:oceanbase://db.example:2881/app",
        "com.oceanbase.jdbc.Driver");
  }

  @Test
  void oceanBasePersistsExplicitCompatibleMode() {
    OceanBaseDataSourcePlugin plugin = new OceanBaseDataSourcePlugin();
    JdbcConnectionProperties oracle =
        (JdbcConnectionProperties)
            plugin.parseConnection(
                "{\"dbType\":\"OCEANBASE\",\"host\":\"127.0.0.1\","
                    + "\"database\":\"app\",\"username\":\"root\","
                    + "\"compatibleMode\":\"ORACLE\"}");

    assertThat(oracle.normalizedJson()).contains("\"compatibleMode\":\"oracle\"");
    FormField compatibleMode =
        plugin.descriptor().connectionForm().allFields().stream()
            .filter(field -> "compatibleMode".equals(field.key()))
            .findFirst()
            .orElseThrow();
    assertThat(compatibleMode.type()).isEqualTo(FieldType.SELECT);
    assertThat(compatibleMode.options())
        .extracting(option -> option.value())
        .containsExactly("mysql", "oracle");
  }

  @Test
  void sqlServerInfersDatabaseFromExplicitJdbcUrl() {
    JdbcConnectionProperties connection =
        (JdbcConnectionProperties)
            new SqlServerDataSourcePlugin()
                .parseConnection(
                    "{\"dbType\":\"SQLSERVER\","
                        + "\"jdbcUrl\":\"jdbc:sqlserver://127.0.0.1:1433;databaseName=warehouse\","
                        + "\"username\":\"sa\"}");

    assertThat(connection.database()).isEqualTo("warehouse");
  }

  private void assertConnection(
      DataSourcePlugin plugin,
      String dbType,
      String expectedUrl,
      String expectedDriver) {
    JdbcConnectionProperties connection =
        (JdbcConnectionProperties)
            plugin.parseConnection(
                "{\"dbType\":\""
                    + dbType
                    + "\",\"host\":\"db.example\",\"database\":\"app\","
                    + "\"username\":\"tester\"}");

    assertThat(connection.jdbcUrl()).isEqualTo(expectedUrl);
    assertThat(connection.driverClassName()).isEqualTo(expectedDriver);
  }
}
