package io.yak.ops.business.sync.offline.engine.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.sync.offline.engine.LinkUpJobSpecFactory;
import io.yak.ops.business.sync.offline.engine.OfflineDefinitionModelAdapter;
import io.yak.ops.business.sync.offline.engine.connector.adapter.JdbcOfflineSyncConnectorAdapter;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapterRegistry;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineExecutionPlanFactoryTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void keepsJdbcMultiTableAsOneNativeMultiJobSpec() throws Exception {
    DataSourceDao dao = mock(DataSourceDao.class);
    when(dao.selectById(1L)).thenReturn(dataSource(1L, "source"));
    when(dao.selectById(2L)).thenReturn(dataSource(2L, "sink"));

    OfflineSyncConnectorAdapterRegistry registry =
        new OfflineSyncConnectorAdapterRegistry(
            List.of(new JdbcOfflineSyncConnectorAdapter(mapper)));
    LinkUpJobSpecFactory jobSpecFactory = new LinkUpJobSpecFactory(dao, mapper, registry);
    OfflineExecutionPlanFactory factory =
        new OfflineExecutionPlanFactory(
            jobSpecFactory,
            registry,
            new OfflineExecutionPlanCodec(mapper),
            mapper);

    JsonNode definition =
        OfflineDefinitionModelAdapter.forJobSpec(
            publicMultiDefinition("jdbc", "jdbc", true), mapper);
    OfflineExecutionPlanFactory.BuildResult result = factory.build(definition);

    assertThat(result.getStrategy()).isEqualTo(OfflineExecutionStrategy.NATIVE_MULTI);
    assertThat(result.getLogicalSpec().path("kind").asText()).isEqualTo("BatchSyncJob");
    assertThat(
            result
                .getLogicalSpec()
                .path("source")
                .path("options")
                .path("table_list")
                .size())
        .isEqualTo(2);
  }

  @Test
  void freezesFanOutWhenCompleteAdapterPairCannotTranslateNativeMulti() throws Exception {
    OfflineSyncConnectorAdapter adapter = new SingleOnlySyntheticAdapter();
    OfflineSyncConnectorAdapterRegistry registry =
        new OfflineSyncConnectorAdapterRegistry(List.of(adapter));
    DataSourceDao dao = mock(DataSourceDao.class);
    LinkUpJobSpecFactory jobSpecFactory = new LinkUpJobSpecFactory(dao, mapper, registry);
    OfflineExecutionPlanCodec codec = new OfflineExecutionPlanCodec(mapper);
    OfflineExecutionPlanFactory factory =
        new OfflineExecutionPlanFactory(jobSpecFactory, registry, codec, mapper);

    JsonNode definition =
        OfflineDefinitionModelAdapter.forJobSpec(
            publicMultiDefinition("synthetic", "synthetic", false), mapper);
    OfflineExecutionPlanFactory.BuildResult result = factory.build(definition);
    OfflineExecutionPlan plan = codec.decode(result.getLogicalSpecJson()).orElseThrow();

    assertThat(result.getStrategy()).isEqualTo(OfflineExecutionStrategy.FAN_OUT);
    assertThat(plan.units()).hasSize(2);
    assertThat(plan.units().get(0).sourceTable()).isEqualTo("business.orders");
    assertThat(plan.units().get(0).sinkTable()).isEqualTo("ods.orders");
    assertThat(
            plan.units()
                .get(0)
                .logicalJobSpec()
                .path("source")
                .path("options")
                .path("table_path")
                .asText())
        .isEqualTo("business.orders");
    assertThat(
            plan.units()
                .get(1)
                .logicalJobSpec()
                .path("sink")
                .path("options")
                .path("table_path")
                .asText())
        .isEqualTo("ods.order_item");
  }

  private JsonNode publicMultiDefinition(
      String sourceConnector, String sinkConnector, boolean withDatasourceIds) throws Exception {
    String ids =
        withDatasourceIds
            ? "\"dataSourceId\":\"1\""
            : "\"dataSourceId\":\"\"";
    String sinkIds =
        withDatasourceIds
            ? "\"dataSourceId\":\"2\""
            : "\"dataSourceId\":\"\"";
    return mapper.readTree(
        """
        {
          "basic": {"jobName":"multi","mode":"GUIDE_MULTI"},
          "source": {
            "connectorId":"%s",
            "dbType":"MYSQL",
            %s,
            "database":"business",
            "tables":["orders","order_item"],
            "tablePattern":"",
            "options":{}
          },
          "sink": {
            "connectorId":"%s",
            "dbType":"MYSQL",
            %s,
            "database":"ods",
            "tableNamingRule":"SAME_NAME",
            "tablePrefix":"",
            "tableSuffix":"",
            "autoCreateTable":false,
            "writeMode":"APPEND",
            "options":{}
          },
          "channel":{"parallelism":2}
        }
        """
            .formatted(sourceConnector, ids, sinkConnector, sinkIds));
  }

  private DataSourcePO dataSource(Long id, String name) {
    DataSourcePO value = new DataSourcePO();
    value.setId(id);
    value.setName(name);
    value.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/demo");
    value.setConnectionParams(
        "{\"driver\":\"com.mysql.cj.jdbc.Driver\",\"username\":\"root\",\"password\":\"secret\"}");
    return value;
  }

  private static final class SingleOnlySyntheticAdapter implements OfflineSyncConnectorAdapter {

    @Override
    public boolean supports(String connectorId, Role role) {
      return "synthetic".equalsIgnoreCase(connectorId);
    }

    @Override
    public boolean requiresDataSource(String connectorId, Role role) {
      return false;
    }

    @Override
    public BuildResult build(BuildContext context) {
      ObjectNode options = context.options();
      if (context.role() == Role.SOURCE) {
        String table = context.config().path("table").asText();
        options.put("table_path", table);
        return new BuildResult(options, table, List.of(table));
      }
      String table = context.config().path("targetTableName").asText();
      options.put("table_path", table);
      return new BuildResult(options, table, List.of());
    }

    @Override
    public void resolveForExecution(ExecutionContext context) {
      // No datasource-owned execution fields for the synthetic adapter.
    }
  }
}
