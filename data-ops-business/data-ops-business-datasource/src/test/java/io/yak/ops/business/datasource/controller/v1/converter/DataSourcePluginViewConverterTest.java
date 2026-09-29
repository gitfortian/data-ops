package io.yak.ops.business.datasource.controller.v1.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor.Capability;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor.FormField;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor.FormSection;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor.JdbcUrlLinkage;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataSourcePluginViewConverterTest {
  @Test
  void descriptorProjectsToLegacyHttpShapeWithoutLeakingSpiTypes() {
    FormField jdbc = new FormField("jdbcUrl", "JDBC 地址", "JDBC_URL", "自动生成", null,
        List.of(), List.of(), List.of("host"), List.of(),
        new JdbcUrlLinkage("jdbc:mysql://{host}:{port}/{database}", "host", "port", "database", true));
    DataSourcePluginDescriptor descriptor = new DataSourcePluginDescriptor(
        DataSourceDbType.MYSQL, "MySQL", "1",
        Set.of(Capability.CONNECTION_TEST, Capability.CATALOG_METADATA),
        List.of(new FormSection("connection", "连接参数", "", false, true, List.of(jdbc))),
        List.of(jdbc), false, null);

    DataSourcePluginConfigVO result = new DataSourcePluginViewConverter().config(descriptor);

    assertThat(result.getPluginType()).isEqualTo("MYSQL");
    assertThat(result.getSections()).hasSize(1);
    assertThat(result.getFormFields()).hasSize(1);
    assertThat(result.getFormFields().getFirst().getType()).isEqualTo("JDBC_URL");
    assertThat(result.getFormFields().getFirst().getUrlLinkage().getTemplate())
        .isEqualTo("jdbc:mysql://{host}:{port}/{database}");
    assertThat(result.getInstallRequired()).isFalse();
  }
}
