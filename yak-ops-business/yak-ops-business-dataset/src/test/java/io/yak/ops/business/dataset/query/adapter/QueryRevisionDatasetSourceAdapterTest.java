package io.yak.ops.business.dataset.query.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskRevisionSnapshot;
import io.yak.ops.business.dataset.query.DatasetQueryCompiler;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter.ExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionTiming;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QueryRevisionDatasetSourceAdapterTest {

  @Test
  void executesPinnedRevisionThroughSqlRuntimeAndTrimsOverflowRow() {
    DatasetTaskCatalogGateway catalog = mock(DatasetTaskCatalogGateway.class);
    SqlExecutionRuntime runtime = mock(SqlExecutionRuntime.class);
    when(catalog.resolveRevision(11L, 71L))
        .thenReturn(
            new DatasetTaskRevisionSnapshot(
                11L,
                71L,
                3,
                "SQL",
                "SELECT region, amount FROM sales",
                "{\"dataSourceId\":\"9\"}"));
    when(runtime.execute(any()))
        .thenReturn(
            new SqlExecutionResult(
                SqlExecutionResultType.RESULT_SET,
                List.of(
                    new SqlExecutionColumn("region", "region", "VARCHAR", Types.VARCHAR, true),
                    new SqlExecutionColumn("amount", "amount", "DECIMAL", Types.DECIMAL, true)),
                List.of(
                    List.of("east", 10),
                    List.of("west", 20),
                    List.of("north", 30)),
                0L,
                false,
                new SqlExecutionTiming(4L, 8L, 12L)));

    QueryRevisionDatasetSourceAdapter adapter =
        new QueryRevisionDatasetSourceAdapter(
            catalog, runtime, new ObjectMapper(), new DatasetQueryCompiler());
    Dataset dataset =
        new Dataset(
            21L, "sales", null, DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version =
        new DatasetVersion(
            31L,
            21L,
            1,
            DatasetSourceType.QUERY_REVISION,
            11L,
            71L,
            3,
            "[]",
            Instant.EPOCH);
    List<DatasetField> fields =
        List.of(
            new DatasetField(
                "region",
                31L,
                "region",
                "region",
                DatasetFieldDataType.STRING,
                true,
                null,
                DatasetFieldRole.DIMENSION,
                1),
            new DatasetField(
                "amount",
                31L,
                "amount",
                "amount",
                DatasetFieldDataType.NUMBER,
                true,
                null,
                DatasetFieldRole.MEASURE,
                2));
    DatasetQueryRequest request =
        new DatasetQueryRequest(null, List.of(), List.of(), List.of(), List.of(), 2, 15);

    ExecutionResult execution = adapter.execute(dataset, version, fields, request);
    DatasetQueryResult result = execution.result();

    verify(catalog).resolveRevision(11L, 71L);
    ArgumentCaptor<SqlExecutionRequest> captor =
        ArgumentCaptor.forClass(SqlExecutionRequest.class);
    verify(runtime).execute(captor.capture());
    assertTrue(
        captor
            .getValue()
            .sql()
            .contains("FROM (SELECT region, amount FROM sales) yak_dataset_source"));
    assertEquals(3, captor.getValue().maxRows());
    assertEquals(15, captor.getValue().timeoutSeconds());
    assertEquals(SqlExecutionCaller.DATASET, captor.getValue().context().caller());
    assertEquals("21", captor.getValue().context().callerReference());
    assertEquals(2, result.returnedRows());
    assertTrue(result.truncated());
    assertEquals(1, result.datasetVersionNo());
    assertEquals("region", result.columns().get(0).name());
    assertEquals("9", execution.dataSourceId());
    assertEquals(captor.getValue().sql(), execution.sql());
    assertTrue(execution.prepareMillis() >= 0L);
    assertEquals(4L, execution.waitMillis());
    assertEquals(8L, execution.executeMillis());
    assertTrue(execution.transferMillis() >= 0L);
  }
}
