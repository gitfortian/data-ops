package io.yak.ops.business.datasource.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.ConnectionForm;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpiDataSourcePluginGatewayTest {

  private final DataSourcePluginRegistry registry = mock(DataSourcePluginRegistry.class);
  private final DataSourceSecretCodec secretCodec = mock(DataSourceSecretCodec.class);
  private final DataSourcePlugin plugin = mock(DataSourcePlugin.class);
  private final DataSourceConnection connection = mock(DataSourceConnection.class);
  private final SpiDataSourcePluginGateway gateway =
      new SpiDataSourcePluginGateway(registry, secretCodec);

  @Test
  void descriptorTranslatesSpiMetadataToBusinessOwnedProjection() {
    when(registry.get(DataSourceDbType.MYSQL)).thenReturn(plugin);
    when(plugin.descriptor()).thenReturn(spiDescriptor());

    DataSourcePluginDescriptor result = gateway.descriptor(DataSourceDbType.MYSQL);

    assertThat(result.dbType()).isEqualTo(DataSourceDbType.MYSQL);
    assertThat(result.apiVersion()).isEqualTo("1");
    assertThat(result.capabilities())
        .contains(DataSourcePluginDescriptor.Capability.SQL_EXECUTION);
  }

  @Test
  void normalizeConnectionTranslatesSpiConnectionToDomainProfile() {
    when(registry.get(DataSourceDbType.MYSQL)).thenReturn(plugin);
    when(plugin.parseConnection("submitted-json")).thenReturn(connection);
    when(connection.jdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/orders");
    when(connection.normalizedJson()).thenReturn("{\"database\":\"orders\"}");

    ConnectionProfile result =
        gateway.normalizeConnection(DataSourceDbType.MYSQL, "submitted-json");

    assertThat(result.jdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:3306/orders");
    assertThat(result.normalizedJson()).isEqualTo("{\"database\":\"orders\"}");
    assertThat(result.originalJson()).isEqualTo(result.normalizedJson());
  }

  @Test
  void connectionFailureIsMappedToBusinessDatasourceException() {
    when(registry.get(DataSourceDbType.MYSQL)).thenReturn(plugin);
    when(plugin.descriptor()).thenReturn(spiDescriptor());
    when(plugin.supports(DataSourceCapability.CONNECTION_TEST)).thenReturn(true);
    when(plugin.parseConnection("{\"database\":\"orders\"}")).thenReturn(connection);
    doThrow(
            new DataSourcePluginException(
                DataSourcePluginException.Operation.CONNECTIVITY,
                "connection unavailable"))
        .when(plugin)
        .testConnection(connection, 3);

    ConnectionProfile profile =
        ConnectionProfile.of(
            "jdbc:mysql://127.0.0.1:3306/orders",
            "{\"database\":\"orders\"}");

    assertThatThrownBy(
            () -> gateway.testConnection(DataSourceDbType.MYSQL, profile, 3))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.CONNECT_FAILED));
  }

  @Test
  void secretMergeStaysInsideSpiAdapterBoundary() {
    io.yak.ops.spi.datasource.DataSourcePluginDescriptor descriptor = spiDescriptor();
    when(registry.get(DataSourceDbType.MYSQL)).thenReturn(plugin);
    when(plugin.descriptor()).thenReturn(descriptor);
    when(secretCodec.mergeStoredSecrets(descriptor, "submitted", "stored"))
        .thenReturn("merged");

    assertThat(gateway.mergeStoredSecrets(DataSourceDbType.MYSQL, "submitted", "stored"))
        .isEqualTo("merged");

    verify(secretCodec).mergeStoredSecrets(descriptor, "submitted", "stored");
  }

  private io.yak.ops.spi.datasource.DataSourcePluginDescriptor spiDescriptor() {
    return new io.yak.ops.spi.datasource.DataSourcePluginDescriptor(
        DataSourceDbType.MYSQL,
        "MySQL",
        "1",
        Set.of(
            DataSourceCapability.CONNECTION_TEST,
            DataSourceCapability.SQL_EXECUTION),
        ConnectionForm.empty(),
        false,
        null);
  }
}
