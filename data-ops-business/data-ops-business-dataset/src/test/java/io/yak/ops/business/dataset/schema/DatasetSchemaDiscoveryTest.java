package io.yak.ops.business.dataset.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.gateway.datasource.DatasetSchemaSqlGateway;
import io.yak.ops.business.dataset.gateway.datasource.DatasetSchemaSqlGateway.QueryColumn;
import io.yak.ops.business.dataset.gateway.datasource.DatasetSchemaSqlGateway.QueryResult;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskRevisionSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceAvailability;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceOrigin;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetSchemaDiscoveryTest {

  @Test
  void discoversStableFieldsFromPinnedRevision() {
    DatasetTaskCatalogGateway taskCatalog = mock(DatasetTaskCatalogGateway.class);
    DatasetSchemaSqlGateway sqlGateway = mock(DatasetSchemaSqlGateway.class);
    DatasetTaskAssetSnapshot asset =
        new DatasetTaskAssetSnapshot(
            11L,
            "sales.sql",
            "101",
            SourceOrigin.DATA_DEVELOPMENT,
            SourceAvailability.ONLINE,
            "SQL",
            71L,
            3);
    when(taskCatalog.resolveRevision(11L, 71L))
        .thenReturn(
            new DatasetTaskRevisionSnapshot(
                11L,
                71L,
                3,
                "SQL",
                "SELECT region, amount FROM sales",
                "{\"dataSourceId\":\"9\"}"));
    when(sqlGateway.execute(
            "9",
            "SELECT yak_dataset_source.* FROM (SELECT region, amount FROM sales) yak_dataset_source LIMIT 1",
            1,
            30))
        .thenReturn(
            new QueryResult(
                true,
                List.of(
                    new QueryColumn("region", "region", "VARCHAR", Types.VARCHAR, true),
                    new QueryColumn("amount", "amount", "DECIMAL", Types.DECIMAL, true)),
                List.of(),
                false));

    DatasetSchemaDiscovery discovery =
        new DatasetSchemaDiscovery(
            taskCatalog, sqlGateway, new DatasetFieldIdentity(), new ObjectMapper());
    List<DatasetFieldSpec> fields = discovery.discover(21L, asset);

    assertEquals(2, fields.size());
    assertEquals(DatasetFieldDataType.STRING, fields.get(0).dataType());
    assertEquals(DatasetFieldRole.DIMENSION, fields.get(0).defaultRole());
    assertEquals(DatasetFieldDataType.NUMBER, fields.get(1).dataType());
    assertEquals(DatasetFieldRole.MEASURE, fields.get(1).defaultRole());
    assertEquals(fields.get(0).fieldId(), discovery.discover(21L, asset).get(0).fieldId());
  }
}
