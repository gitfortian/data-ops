package io.yak.ops.business.dataset.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceRecorder;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter.ExecutionResult;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetQueryCoordinatorTest {

  @Test
  void currentQueryUsesExactCurrentVersionAndRecordsPerformanceEvidence() {
    Fixture fixture = fixture();
    Dataset dataset = dataset(21L, 32L);
    DatasetVersion current = version(32L, 21L, 2, DatasetSourceType.SQL_QUERY);
    List<DatasetField> fields = List.of();
    DatasetQueryResult rawResult =
        new DatasetQueryResult(21L, 32L, 2, List.of(), List.of(), List.of(), 0, false, 7L);
    when(fixture.repository().findDataset(21L)).thenReturn(Optional.of(dataset));
    when(fixture.repository().findVersion(32L)).thenReturn(Optional.of(current));
    when(fixture.repository().listFields(32L)).thenReturn(fields);
    when(fixture.registry().require(DatasetSourceType.SQL_QUERY)).thenReturn(fixture.adapter());
    when(fixture.adapter().execute(dataset, current, fields, null))
        .thenReturn(new ExecutionResult(rawResult, "ds-1", "select 1", 1L, 2L, 3L, 4L));

    DatasetQueryResult result = fixture.coordinator().query(21L, null);

    assertNotNull(result.queryId());
    assertEquals(32L, result.datasetVersionId());
    ArgumentCaptor<DatasetQueryPerformance> trace = trace(fixture.recorder());
    assertEquals(32L, trace.getValue().datasetVersionId());
    assertEquals(2, trace.getValue().datasetVersionNo());
    assertEquals(DatasetQueryStatus.SUCCESS, trace.getValue().status());
    assertNull(trace.getValue().failureStage());
  }

  @Test
  void requestedVersionDoesNotDriftToCurrentPointer() {
    Fixture fixture = fixture();
    Dataset dataset = dataset(21L, 32L);
    DatasetVersion v1 = version(31L, 21L, 1, DatasetSourceType.QUERY_REVISION);
    DatasetQueryRequest request =
        new DatasetQueryRequest(1, List.of(), List.of(), List.of(), List.of(), 10, null);
    when(fixture.repository().findDataset(21L)).thenReturn(Optional.of(dataset));
    when(fixture.repository().findVersion(21L, 1)).thenReturn(Optional.of(v1));
    when(fixture.repository().listFields(31L)).thenReturn(List.of());
    when(fixture.registry().require(DatasetSourceType.QUERY_REVISION)).thenReturn(fixture.adapter());
    when(fixture.adapter().execute(any(), any(), any(), any()))
        .thenReturn(new ExecutionResult(
            new DatasetQueryResult(21L, 31L, 1, List.of(), List.of(), List.of(), 0, false, 1L),
            "ds-1", "select 1", 0L, 0L, 1L, 0L));

    DatasetQueryResult result = fixture.coordinator().query(21L, request);

    assertEquals(31L, result.datasetVersionId());
    verify(fixture.repository()).findVersion(21L, 1);
    verify(fixture.repository(), never()).listVersions(21L);
  }

  @Test
  void rejectedRequestStillRecordsTerminalTrace() {
    Fixture fixture = fixture();

    assertThrows(IllegalArgumentException.class, () -> fixture.coordinator().query(0L, null));

    ArgumentCaptor<DatasetQueryPerformance> trace = trace(fixture.recorder());
    assertNotNull(trace.getValue().queryId());
    assertEquals(DatasetQueryStatus.REJECTED, trace.getValue().status());
    assertEquals("VALIDATE_REQUEST", trace.getValue().failureStage());
    assertNull(trace.getValue().datasetVersionId());
  }

  @Test
  void sqlTimeoutRecordsTimeoutAndRethrowsOriginalFailure() {
    Fixture fixture = fixture();
    Dataset dataset = dataset(21L, 32L);
    DatasetVersion current = version(32L, 21L, 2, DatasetSourceType.SQL_QUERY);
    RuntimeException timeout = new RuntimeException(new SQLTimeoutException("query timed out"));
    when(fixture.repository().findDataset(21L)).thenReturn(Optional.of(dataset));
    when(fixture.repository().findVersion(32L)).thenReturn(Optional.of(current));
    when(fixture.repository().listFields(32L)).thenReturn(List.of());
    when(fixture.registry().require(DatasetSourceType.SQL_QUERY)).thenReturn(fixture.adapter());
    when(fixture.adapter().execute(dataset, current, List.of(), null)).thenThrow(timeout);

    RuntimeException thrown = assertThrows(
        RuntimeException.class, () -> fixture.coordinator().query(21L, null));

    assertSame(timeout, thrown);
    ArgumentCaptor<DatasetQueryPerformance> trace = trace(fixture.recorder());
    assertEquals(DatasetQueryStatus.TIMEOUT, trace.getValue().status());
    assertEquals("EXECUTE_SOURCE", trace.getValue().failureStage());
    assertEquals(32L, trace.getValue().datasetVersionId());
  }

  private ArgumentCaptor<DatasetQueryPerformance> trace(DatasetQueryPerformanceRecorder recorder) {
    ArgumentCaptor<DatasetQueryPerformance> trace =
        ArgumentCaptor.forClass(DatasetQueryPerformance.class);
    verify(recorder).record(trace.capture());
    return trace;
  }

  private Fixture fixture() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetSourceQueryRegistry registry = mock(DatasetSourceQueryRegistry.class);
    DatasetQueryPerformanceRecorder recorder = mock(DatasetQueryPerformanceRecorder.class);
    DatasetSourceQueryAdapter adapter = mock(DatasetSourceQueryAdapter.class);
    return new Fixture(
        repository,
        registry,
        recorder,
        adapter,
        new DatasetQueryCoordinator(repository, registry, recorder));
  }

  private Dataset dataset(long datasetId, long currentVersionId) {
    return new Dataset(
        datasetId,
        "sales",
        null,
        DatasetStatus.ONLINE,
        currentVersionId,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private DatasetVersion version(
      long versionId, long datasetId, int versionNo, DatasetSourceType sourceType) {
    return new DatasetVersion(
        versionId,
        datasetId,
        versionNo,
        sourceType,
        sourceType == DatasetSourceType.QUERY_REVISION ? 11L : 0L,
        sourceType == DatasetSourceType.QUERY_REVISION ? 71L : 0L,
        sourceType == DatasetSourceType.QUERY_REVISION ? 3 : 0,
        sourceType == DatasetSourceType.SQL_QUERY ? "ds-1" : null,
        sourceType == DatasetSourceType.SQL_QUERY ? "select 1" : null,
        "[]",
        Instant.EPOCH);
  }

  private record Fixture(
      DatasetRepository repository,
      DatasetSourceQueryRegistry registry,
      DatasetQueryPerformanceRecorder recorder,
      DatasetSourceQueryAdapter adapter,
      DatasetQueryCoordinator coordinator) {}
}
