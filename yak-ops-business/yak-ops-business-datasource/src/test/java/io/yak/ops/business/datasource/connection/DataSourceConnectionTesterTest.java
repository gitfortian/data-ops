package io.yak.ops.business.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceConnectionTesterTest {

  @Mock private DataSourceReader reader;
  @Mock private DataSourceRepository repository;
  @Mock private DataSourceConnectionResolver resolver;
  @Mock private DataSourcePluginGateway pluginGateway;
  @Mock private DataSourceProperties properties;

  @Test
  void savedConnectionParameterFailureDoesNotOverwriteExistingStatus() {
    DataSourceDefinition dataSource = savedDataSource(DataSourceConnStatus.CONNECTED);
    when(reader.require(42L)).thenReturn(dataSource);
    when(properties.getConnectionTest()).thenReturn(new DataSourceProperties.ConnectionTest());
    doThrow(
            new DataSourceException(
                DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
                "invalid stored connection"))
        .when(pluginGateway)
        .testConnection(
            eq(DataSourceDbType.MYSQL),
            any(ConnectionProfile.class),
            anyInt());

    assertThatThrownBy(() -> tester().testSaved(42L))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.INVALID_CONNECTION_PARAMS));

    assertThat(dataSource.getConnStatus()).isEqualTo(DataSourceConnStatus.CONNECTED);
    verify(repository, never()).updateConnectionStatus(any(), any());
  }

  @Test
  void savedConnectivityFailureMarksCurrentConfigurationDisconnected() {
    DataSourceDefinition dataSource = savedDataSource(DataSourceConnStatus.CONNECTED);
    when(reader.require(42L)).thenReturn(dataSource);
    when(properties.getConnectionTest()).thenReturn(new DataSourceProperties.ConnectionTest());
    doThrow(
            new DataSourceException(
                DataSourceErrorCode.CONNECT_FAILED,
                "connection unavailable"))
        .when(pluginGateway)
        .testConnection(
            eq(DataSourceDbType.MYSQL),
            any(ConnectionProfile.class),
            anyInt());

    assertThatThrownBy(() -> tester().testSaved(42L))
        .isInstanceOf(DataSourceException.class);

    assertThat(dataSource.getConnStatus()).isEqualTo(DataSourceConnStatus.DISCONNECTED);
    verify(repository).updateConnectionStatus(42L, DataSourceConnStatus.DISCONNECTED);
  }

  private DataSourceConnectionTester tester() {
    return new DataSourceConnectionTester(
        reader,
        repository,
        resolver,
        pluginGateway,
        properties);
  }

  private DataSourceDefinition savedDataSource(DataSourceConnStatus status) {
    return DataSourceDefinition.restore(
        42L,
        "orders-db",
        DataSourceDbType.MYSQL,
        "jdbc:mysql://127.0.0.1:3306/orders",
        DataSourceEnvironment.PROD,
        status,
        null,
        "{\"database\":\"orders\"}",
        "{\"database\":\"orders\"}",
        null,
        null);
  }
}
