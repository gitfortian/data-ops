package io.yak.ops.plugin.database.jdbc.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.SshTunnelConfig;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormSection;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import org.junit.jupiter.api.Test;

class MySqlDataSourcePluginTest {

  @Test
  void shouldUseMysqlConnectorJDefaults() {
    DataSourceConnection connection =
        new MySqlDataSourcePlugin()
            .parseConnection(
                "{\"dbType\":\"MYSQL\",\"host\":\"127.0.0.1\","
                    + "\"database\":\"demo\",\"username\":\"test_user\"}");

    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:3306/demo");
    assertThat(connection.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(new MySqlDataSourcePlugin().acceptsUrl(connection.jdbcUrl())).isTrue();
    assertThat(new MySqlDataSourcePlugin().acceptsUrl("jdbc:mariadb://127.0.0.1/demo"))
        .isFalse();
  }

  @Test
  void shouldExposeStableDescriptorCapabilitiesAndSectionedFormSchema() {
    DataSourcePluginDescriptor descriptor = new MySqlDataSourcePlugin().descriptor();

    assertThat(descriptor.apiVersion()).isEqualTo(DataSourcePluginDescriptor.CURRENT_API_VERSION);
    assertThat(descriptor.capabilities())
        .contains(
            DataSourceCapability.CONNECTION_TEST,
            DataSourceCapability.CATALOG_METADATA,
            DataSourceCapability.CATALOG_READ,
            DataSourceCapability.SQL_EXECUTION,
            DataSourceCapability.TRANSACTIONS,
            DataSourceCapability.SSH_TUNNEL);
    assertThat(descriptor.connectionForm().sections())
        .extracting(FormSection::key)
        .containsExactly("connection", "ssh", "driver", "advanced");
    assertThat(descriptor.connectionForm().sections().get(0).collapsible()).isFalse();
    assertThat(descriptor.connectionForm().sections().get(1).collapsible()).isTrue();
    assertThat(descriptor.connectionForm().sections().get(1).defaultExpanded()).isFalse();
    assertThat(descriptor.connectionForm().sections().get(1).fields())
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.key()).isEqualTo("sshTunnel");
              assertThat(field.type()).isEqualTo(FieldType.SSH);
            });

    FormField jdbcUrlField =
        descriptor.connectionForm().sections().get(0).fields().stream()
            .filter(field -> "jdbcUrl".equals(field.key()))
            .findFirst()
            .orElseThrow();
    assertThat(jdbcUrlField.type()).isEqualTo(FieldType.JDBC_URL);
    assertThat(jdbcUrlField.jdbcUrlLinkage()).isNotNull();
    assertThat(jdbcUrlField.jdbcUrlLinkage().template())
        .isEqualTo("jdbc:mysql://{host}:{port}/{database}");
    assertThat(jdbcUrlField.jdbcUrlLinkage().hostField()).isEqualTo("host");
    assertThat(jdbcUrlField.jdbcUrlLinkage().portField()).isEqualTo("port");
    assertThat(jdbcUrlField.jdbcUrlLinkage().databaseField()).isEqualTo("database");
    assertThat(jdbcUrlField.jdbcUrlLinkage().preserveSuffix()).isTrue();
    assertThat(jdbcUrlField.dependsOn()).contains("sshTunnel");
    assertThat(jdbcUrlField.visibleWhen())
        .singleElement()
        .satisfies(
            condition -> {
              assertThat(condition.field()).isEqualTo("sshTunnel.enabled");
              assertThat(condition.operator().name()).isEqualTo("FALSY");
            });

    FormField propertiesField =
        descriptor.connectionForm().sections().get(3).fields().stream()
            .filter(field -> "properties".equals(field.key()))
            .findFirst()
            .orElseThrow();
    assertThat(propertiesField.dependsOn()).containsExactly("driverClassName");
    assertThat(propertiesField.visibleWhen())
        .singleElement()
        .satisfies(
            condition -> {
              assertThat(condition.field()).isNull();
              assertThat(condition.operator().name()).isEqualTo("TRUTHY");
            });

    assertThat(descriptor.secretFieldKeys()).contains("password");
    assertThat(descriptor.connectionForm().legacyFields()).hasSize(9);
  }

  @Test
  void shouldParseStandardSshTunnelConfig() {
    JdbcConnectionProperties connection =
        (JdbcConnectionProperties)
            new MySqlDataSourcePlugin()
                .parseConnection(
                    "{\"dbType\":\"MYSQL\",\"host\":\"db.internal\","
                        + "\"port\":3306,\"database\":\"demo\",\"username\":\"test_user\","
                        + "\"sshTunnel\":{\"enabled\":true,\"host\":\"bastion.example.invalid\","
                        + "\"port\":22,\"username\":\"test_ops\",\"authType\":\"PASSWORD\","
                        + "\"password\":\"TEST_ONLY_VALUE\"}}}");

    assertThat(connection.host()).isEqualTo("db.internal");
    assertThat(connection.port()).isEqualTo(3306);
    assertThat(connection.sshTunnel().enabled()).isTrue();
    assertThat(connection.sshTunnel().host()).isEqualTo("bastion.example.invalid");
    assertThat(connection.sshTunnel().port()).isEqualTo(22);
    assertThat(connection.sshTunnel().username()).isEqualTo("test_ops");
    assertThat(connection.sshTunnel().authType()).isEqualTo(SshTunnelConfig.AuthType.PASSWORD);
    assertThat(connection.normalizedJson()).contains("\"sshTunnel\"");
  }

  @Test
  void shouldRejectCustomJdbcUrlWhenSshTunnelIsEnabled() {
    assertThatThrownBy(
            () ->
                new MySqlDataSourcePlugin()
                    .parseConnection(
                        "{\"dbType\":\"MYSQL\",\"host\":\"db.internal\","
                            + "\"database\":\"demo\",\"username\":\"test_user\","
                            + "\"jdbcUrl\":\"jdbc:mysql://db.internal:3306/demo\","
                            + "\"sshTunnel\":{\"enabled\":true,\"host\":\"bastion.example.invalid\","
                            + "\"username\":\"test_ops\",\"password\":\"TEST_ONLY_VALUE\"}}}"))
        .isInstanceOf(DataSourcePluginException.class)
        .hasMessageContaining("启用 SSH 隧道时");
  }
}
