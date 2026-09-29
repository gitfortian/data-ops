package io.yak.ops.plugin.database.doris;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import org.junit.jupiter.api.Test;

class DorisDataSourcePluginTest {

  @Test
  void shouldKeepDorisDefaultsInsideDorisPlugin() {
    DataSourceConnection connection =
        new DorisDataSourcePlugin()
            .parseConnection(
                "{\"dbType\":\"DORIS\",\"host\":\"doris-fe\","
                    + "\"database\":\"warehouse\",\"username\":\"test_user\","
                    + "\"fenodes\":\"doris-fe:8030\"}");

    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://doris-fe:9030/warehouse");
    assertThat(connection.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(connection.normalizedJson()).contains("\"fenodes\":\"doris-fe:8030\"");
    assertThat(new DorisDataSourcePlugin().createCatalog(connection, 5))
        .isInstanceOf(DorisJdbcCatalog.class);
  }

  @Test
  void shouldExposeDorisDescriptorAndSpecificFormField() {
    DataSourcePluginDescriptor descriptor = new DorisDataSourcePlugin().descriptor();

    assertThat(descriptor.apiVersion()).isEqualTo(DataSourcePluginDescriptor.CURRENT_API_VERSION);
    assertThat(descriptor.capabilities())
        .contains(
            DataSourceCapability.CATALOG_METADATA,
            DataSourceCapability.CATALOG_READ,
            DataSourceCapability.SQL_EXECUTION,
            DataSourceCapability.TRANSACTIONS);
    assertThat(descriptor.connectionForm().legacyFields())
        .anyMatch(field -> "fenodes".equals(field.key()));
  }
}
