package io.yak.ops.business.sync.offline.engine.connector.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildResult;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class NativeOlapConnectorAdapterTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void clickHouseBuildAndExecutionTranslateSingleTableDatasource() {
    ClickHouseOfflineSyncConnectorAdapter adapter = new ClickHouseOfflineSyncConnectorAdapter(mapper);
    ObjectNode config = mapper.createObjectNode();
    config.put("table", "orders");
    ObjectNode options = mapper.createObjectNode();

    BuildResult built = adapter.build(
        new BuildContext(
            "clickhouse",
            Role.SOURCE,
            "GUIDE_SINGLE",
            "orders-sync",
            config,
            mapper.createObjectNode(),
            options,
            5000,
            2000,
            List.of()));

    DataSourcePO dataSource = dataSource(
        10L,
        DataSourceDbType.CLICKHOUSE,
        "{\"host\":\"clickhouse.internal\",\"port\":8123,\"database\":\"analytics\","
            + "\"username\":\"default\",\"password\":\"secret\","
            + "\"serverTimeZone\":\"Asia/Shanghai\"}");
    adapter.resolveForExecution(
        new ExecutionContext("clickhouse", Role.SOURCE, "来源端", dataSource, built.options()));

    assertThat(built.options().path("table_path").asText()).isEqualTo("analytics.orders");
    assertThat(built.options().path("host").asText()).isEqualTo("clickhouse.internal:8123");
    assertThat(built.options().path("batch_size").asInt()).isEqualTo(2000);
    assertThat(built.options().path("server_time_zone").asText()).isEqualTo("Asia/Shanghai");
    assertThat(built.options().path("password").asText()).isEqualTo("secret");
  }

  @Test
  void starRocksSourceInjectsSchemaFromDatasourceCatalogAtExecutionTime() {
    DataSourceCatalogReader catalogReader = mock(DataSourceCatalogReader.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DataSourceCatalogReader> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(catalogReader);
    when(catalogReader.listColumns(20L, "analytics", null, "orders"))
        .thenReturn(
            List.of(
                new CatalogColumn("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null),
                new CatalogColumn("amount", "DECIMAL", Types.DECIMAL, 18, 2, true, 2, false, null)));

    StarRocksOfflineSyncConnectorAdapter adapter =
        new StarRocksOfflineSyncConnectorAdapter(mapper, provider);
    ObjectNode config = mapper.createObjectNode();
    config.put("table", "analytics.orders");
    BuildResult built = adapter.build(
        new BuildContext(
            "starrocks",
            Role.SOURCE,
            "GUIDE_SINGLE",
            "orders-sync",
            config,
            mapper.createObjectNode(),
            mapper.createObjectNode(),
            1000,
            4096,
            List.of()));

    DataSourcePO dataSource = dataSource(
        20L,
        DataSourceDbType.STARROCKS,
        "{\"database\":\"analytics\",\"username\":\"root\",\"password\":\"secret\","
            + "\"nodeUrls\":\"fe-a:8030,fe-b:8030\"}");
    adapter.resolveForExecution(
        new ExecutionContext("starrocks", Role.SOURCE, "来源端", dataSource, built.options()));

    assertThat(built.options().path("node_urls").size()).isEqualTo(2);
    assertThat(built.options().path("scan_batch_rows").asInt()).isEqualTo(4096);
    assertThat(built.options().path("schema.fields").path("id").asText()).isEqualTo("BIGINT");
    assertThat(built.options().path("schema.fields").path("amount").asText())
        .isEqualTo("DECIMAL(18,2)");
  }

  @Test
  void nativeAdaptersRejectMultiTableUntilFanOutStage() {
    ClickHouseOfflineSyncConnectorAdapter adapter = new ClickHouseOfflineSyncConnectorAdapter(mapper);
    ObjectNode config = mapper.createObjectNode();
    config.put("table", "analytics.orders");

    assertThatThrownBy(
            () ->
                adapter.build(
                    new BuildContext(
                        "clickhouse",
                        Role.SOURCE,
                        "GUIDE_MULTI",
                        "orders-sync",
                        config,
                        mapper.createObjectNode(),
                        mapper.createObjectNode(),
                        1000,
                        1000,
                        List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("仅支持单表离线同步");
  }

  private DataSourcePO dataSource(Long id, DataSourceDbType dbType, String connectionParams) {
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(id);
    dataSource.setName(dbType.getDisplayName());
    dataSource.setDbType(dbType);
    dataSource.setConnectionParams(connectionParams);
    return dataSource;
  }
}
