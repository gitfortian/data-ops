package io.yak.ops.business.datasource.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import org.junit.jupiter.api.Test;

class DataSourceDefinitionTest {

  @Test
  void createStartsWithUnknownConnectionStatus() {
    DataSourceDefinition dataSource =
        DataSourceDefinition.create(
            "orders-db",
            DataSourceDbType.MYSQL,
            profile("orders", "secret-a"),
            DataSourceEnvironment.PROD,
            "orders");

    assertThat(dataSource.getName()).isEqualTo("orders-db");
    assertThat(dataSource.getConnStatus()).isEqualTo(DataSourceConnStatus.UNKNOWN);
    assertThat(dataSource.connectionProfile().normalizedJson()).contains("secret-a");
  }

  @Test
  void changingConnectionProfileInvalidatesPreviousConnectionStatus() {
    DataSourceDefinition dataSource = dataSource();
    dataSource.markConnected();

    dataSource.updateConfiguration(
        "orders-db-v2",
        DataSourceDbType.MYSQL,
        profile("orders_v2", "secret-b"),
        DataSourceEnvironment.TEST,
        "updated");

    assertThat(dataSource.getConnStatus()).isEqualTo(DataSourceConnStatus.UNKNOWN);
    assertThat(dataSource.getName()).isEqualTo("orders-db-v2");
    assertThat(dataSource.getEnvironment()).isEqualTo(DataSourceEnvironment.TEST);
    assertThat(dataSource.getConnectionParams()).contains("secret-b");
  }

  @Test
  void dataSourceTypeCannotChangeAfterCreation() {
    DataSourceDefinition dataSource = dataSource();

    assertThatThrownBy(
            () ->
                dataSource.updateConfiguration(
                    "orders-db",
                    DataSourceDbType.POSTGRE_SQL,
                    profile("orders", "secret-b"),
                    DataSourceEnvironment.PROD,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不允许修改数据源类型");

    assertThat(dataSource.getDbType()).isEqualTo(DataSourceDbType.MYSQL);
    assertThat(dataSource.getConnectionParams()).contains("secret-a");
  }

  @Test
  void connectionStatusBehaviorTracksLatestSavedConfigurationTest() {
    DataSourceDefinition dataSource = dataSource();

    dataSource.markConnected();
    assertThat(dataSource.getConnStatus()).isEqualTo(DataSourceConnStatus.CONNECTED);

    dataSource.markDisconnected();
    assertThat(dataSource.getConnStatus()).isEqualTo(DataSourceConnStatus.DISCONNECTED);

    dataSource.markConnectionUnknown();
    assertThat(dataSource.getConnStatus()).isEqualTo(DataSourceConnStatus.UNKNOWN);
  }

  @Test
  void aggregateAndConnectionProfileToStringDoNotLeakConnectionSecrets() {
    DataSourceDefinition dataSource = dataSource();
    ConnectionProfile profile = dataSource.connectionProfile();

    assertThat(dataSource.toString()).doesNotContain("secret-a").doesNotContain("root");
    assertThat(profile.toString()).doesNotContain("secret-a").doesNotContain("root");
  }

  private DataSourceDefinition dataSource() {
    return DataSourceDefinition.create(
        "orders-db",
        DataSourceDbType.MYSQL,
        profile("orders", "secret-a"),
        DataSourceEnvironment.PROD,
        null);
  }

  private ConnectionProfile profile(String database, String password) {
    String json =
        "{\"host\":\"127.0.0.1\",\"username\":\"root\",\"password\":\""
            + password
            + "\"}";
    return new ConnectionProfile(
        "jdbc:mysql://root:" + password + "@127.0.0.1:3306/" + database,
        json,
        json);
  }
}
