package io.yak.ops.business.datasource.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataSourcePluginReaderTest {

  @Test
  void readsPluginDescriptorThroughBusinessGateway() {
    DataSourcePluginGateway gateway = mock(DataSourcePluginGateway.class);
    DataSourcePluginDescriptor descriptor = descriptor();
    when(gateway.descriptor(DataSourceDbType.MYSQL)).thenReturn(descriptor);

    DataSourcePluginReader reader = new DataSourcePluginReader(gateway);

    assertThat(reader.get("mysql")).isSameAs(descriptor);
    assertThat(reader.install("MYSQL")).isTrue();
    verify(gateway, org.mockito.Mockito.times(2)).descriptor(DataSourceDbType.MYSQL);
  }

  private DataSourcePluginDescriptor descriptor() {
    return new DataSourcePluginDescriptor(
        DataSourceDbType.MYSQL,
        "MySQL",
        "1",
        Set.of(DataSourcePluginDescriptor.Capability.CONNECTION_TEST),
        List.of(),
        List.of(),
        false,
        null);
  }
}
