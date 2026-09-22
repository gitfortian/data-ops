package io.yak.ops.business.dataset.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetQueryPerformancePO;
import io.yak.ops.business.dataset.repository.DatasetQueryPerformanceStoreAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetQueryPerformanceStoreAdapterTest {

  @Test
  void mapsDomainTraceThroughDatasetDaoWithoutExposingMybatisToObservability() {
    DatasetDao dao = mock(DatasetDao.class);
    DatasetQueryPerformanceStoreAdapter adapter = new DatasetQueryPerformanceStoreAdapter(dao);
    when(dao.insertQueryPerformance(any())).thenReturn(1);
    DatasetQueryPerformance trace = new DatasetQueryPerformance(
        "q1", 7L, "orders", 11L, 3, "SQL_QUERY", "ds-1", "select * from t",
        "abc", DatasetQueryStatus.SUCCESS, null, null, null,
        1L, 2L, 3L, 4L, 10L, 5, false, Instant.EPOCH, Instant.EPOCH.plusMillis(10));

    adapter.append(9L, trace);

    ArgumentCaptor<DatasetQueryPerformancePO> row =
        ArgumentCaptor.forClass(DatasetQueryPerformancePO.class);
    verify(dao).insertQueryPerformance(row.capture());
    assertEquals(9L, row.getValue().getProjectId());
    assertEquals("q1", row.getValue().getQueryId());
    assertEquals("SUCCESS", row.getValue().getStatus());
    assertEquals("abc", row.getValue().getSqlHash());
  }

  @Test
  void mapsNullableEarlyFailureVersionEvidenceBackToDomain() {
    DatasetDao dao = mock(DatasetDao.class);
    DatasetQueryPerformanceStoreAdapter adapter = new DatasetQueryPerformanceStoreAdapter(dao);
    DatasetQueryPerformancePO row = new DatasetQueryPerformancePO();
    row.setQueryId("q2");
    row.setDatasetId(7L);
    row.setStatus("REJECTED");
    row.setFailureStage("VALIDATE_REQUEST");
    row.setWaitMillis(0L);
    row.setPrepareMillis(0L);
    row.setExecuteMillis(0L);
    row.setTransferMillis(0L);
    row.setTotalMillis(1L);
    row.setReturnedRows(0);
    row.setTruncated(false);
    row.setStartedAt(Timestamp.from(Instant.EPOCH));
    row.setFinishedAt(Timestamp.from(Instant.EPOCH.plusMillis(1)));
    when(dao.selectQueryPerformance(9L, Set.of(7L), Set.of(), List.of("REJECTED"), 1L, 10))
        .thenReturn(List.of(row));

    List<DatasetQueryPerformance> traces = adapter.recent(
        9L, Set.of(7L), Set.of(), Set.of(DatasetQueryStatus.REJECTED), 1L, 10);

    assertEquals(1, traces.size());
    assertEquals(DatasetQueryStatus.REJECTED, traces.get(0).status());
    assertNull(traces.get(0).datasetVersionId());
    assertNull(traces.get(0).datasetVersionNo());
  }
}
