package io.yak.ops.business.datasource.controller.v1.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourcePluginReader;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import org.junit.jupiter.api.Test;

class DataSourceViewConverterTest {
  @Test
  void detailKeepsJdbcAndConnectionJsonMasked() {
    DataSourcePluginReader pluginReader = mock(DataSourcePluginReader.class);
    DataSourceViewConverter converter = new DataSourceViewConverter(pluginReader);
    DataSourceDefinition source = DataSourceDefinition.restore(
        42L,
        "orders-db",
        DataSourceDbType.MYSQL,
        "jdbc:mysql://user:credential-value@127.0.0.1:3306/orders",
        DataSourceEnvironment.PROD,
        DataSourceConnStatus.CONNECTED,
        null,
        "{\"password\":\"credential-value\"}",
        "{\"password\":\"credential-value\"}",
        null,
        null);

    when(pluginReader.maskSensitiveText(source.getJdbcUrl()))
        .thenReturn("jdbc:mysql://user:******@127.0.0.1:3306/orders");
    when(pluginReader.maskConnectionJson(DataSourceDbType.MYSQL, source.getOriginalJson()))
        .thenReturn("{\"password\":\"******\"}");

    DataSourceVO result = converter.definition(source, true);

    assertThat(result.getJdbcUrl()).doesNotContain("credential-value");
    assertThat(result.getOriginalJson()).doesNotContain("credential-value");
    assertThat(result.getEnvironmentName()).isEqualTo("生产");
    verify(pluginReader).maskSensitiveText(source.getJdbcUrl());
    verify(pluginReader).maskConnectionJson(DataSourceDbType.MYSQL, source.getOriginalJson());
  }
}
