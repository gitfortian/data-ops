package io.yak.ops.business.sync.offline.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildResult;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapterRegistry;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkUpJobSpecFactoryAdapterDelegationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void dedicatedAdapterOwnsBuildAndExecutionDatasourceTranslation() throws Exception {
    DataSourceDao dao = mock(DataSourceDao.class);
    when(dao.selectById(7L)).thenReturn(dataSource(7L, "native-source"));
    when(dao.selectById(8L)).thenReturn(dataSource(8L, "native-sink"));

    OfflineSyncConnectorAdapter adapter = new NativeDemoAdapter();
    OfflineSyncConnectorAdapterRegistry registry =
        new OfflineSyncConnectorAdapterRegistry(List.of(adapter));

    LinkUpJobSpecFactory factory = new LinkUpJobSpecFactory(dao, mapper, registry);
    JsonNode definition =
        mapper.readTree(
            """
            {
              "basic": {"jobName": "native-demo", "mode": "GUIDE_SINGLE"},
              "source": {
                "connectorId": "native-demo",
                "dataSourceId": 7,
                "config": {
                  "connectorOptions": {"table_path": "src.orders"}
                }
              },
              "sink": {
                "connectorId": "native-demo",
                "dataSourceId": 8,
                "config": {
                  "connectorOptions": {"table_path": "dst.orders"}
                }
              },
              "channel": {"parallelism": 1}
            }
            """);

    LinkUpJobSpecFactory.BuildResult built = factory.build(definition);
    JsonNode logical = built.getJobSpec();

    assertThat(logical.path("source").path("dataSourceRef").path("id").asLong())
        .isEqualTo(7L);
    assertThat(logical.path("sink").path("dataSourceRef").path("id").asLong())
        .isEqualTo(8L);
    assertThat(logical.path("source").path("options").path("built_by_adapter").asText())
        .isEqualTo("SOURCE");
    assertThat(logical.path("sink").path("options").path("built_by_adapter").asText())
        .isEqualTo("SINK");
    assertThat(logical.toString()).doesNotContain("native-source", "native-sink");

    JsonNode resolved = factory.resolveForExecution(logical);

    assertThat(resolved.path("source").has("dataSourceRef")).isFalse();
    assertThat(resolved.path("sink").has("dataSourceRef")).isFalse();
    assertThat(resolved.path("source").path("options").path("resolved_from").asText())
        .isEqualTo("native-source");
    assertThat(resolved.path("sink").path("options").path("resolved_from").asText())
        .isEqualTo("native-sink");
  }

  private DataSourcePO dataSource(Long id, String name) {
    DataSourcePO value = new DataSourcePO();
    value.setId(id);
    value.setName(name);
    return value;
  }

  private static final class NativeDemoAdapter implements OfflineSyncConnectorAdapter {

    @Override
    public boolean supports(String connectorId, Role role) {
      return "native-demo".equals(connectorId);
    }

    @Override
    public boolean requiresDataSource(String connectorId, Role role) {
      return true;
    }

    @Override
    public BuildResult build(BuildContext context) {
      context.options().put("built_by_adapter", context.role().name());
      List<String> sourceTables =
          context.role() == Role.SOURCE ? List.of("src.orders") : List.of();
      return new BuildResult(
          context.options(),
          context.role() == Role.SOURCE ? "src.orders" : "dst.orders",
          sourceTables);
    }

    @Override
    public void resolveForExecution(ExecutionContext context) {
      context.options().put("resolved_from", context.dataSource().getName());
    }
  }
}
