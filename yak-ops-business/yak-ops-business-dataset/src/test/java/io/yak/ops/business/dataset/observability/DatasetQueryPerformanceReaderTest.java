package io.yak.ops.business.dataset.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatasetQueryPerformanceReaderTest {

  @Test
  void filtersRecentTracesByDatasetAndKeepsNewestFirst() {
    Fixture fixture = fixture();
    fixture.record(trace("q1", 1L, 10L));
    fixture.record(trace("q2", 2L, 20L));
    fixture.record(trace("q3", 1L, 30L));

    var traces = fixture.reader.recent(Set.of(1L), 10);

    assertEquals(2, traces.size());
    assertEquals("q3", traces.get(0).queryId());
    assertEquals("q1", traces.get(1).queryId());
  }

  @Test
  void filtersRecentTracesByExactQueryIds() {
    Fixture fixture = fixture();
    fixture.record(trace("q1", 1L, 10L));
    fixture.record(trace("q2", 1L, 20L));
    fixture.record(trace("q3", 2L, 30L));

    var traces = fixture.reader.recent(Set.of(), Set.of("q1", "q3"), 10);

    assertEquals(2, traces.size());
    assertEquals("q3", traces.get(0).queryId());
    assertEquals("q1", traces.get(1).queryId());
  }

  @Test
  void filtersByTerminalStatusAndSlowQueryThreshold() {
    DatasetQueryPerformanceBuffer buffer = new DatasetQueryPerformanceBuffer();
    buffer.add(7L, failedTrace("failed-fast", 1L, DatasetQueryStatus.FAILED, 200L));
    buffer.add(7L, failedTrace("timeout-slow", 1L, DatasetQueryStatus.TIMEOUT, 5_000L));
    buffer.add(7L, trace("success-slow", 1L, 6_000L));
    DatasetQueryPerformanceReader reader = scopedReader(buffer, 7L);

    var traces = reader.recent(
        Set.of(1L), Set.of(), Set.of(DatasetQueryStatus.TIMEOUT), 3_000L, 10);

    assertEquals(1, traces.size());
    assertEquals("timeout-slow", traces.get(0).queryId());
  }

  @Test
  void localFallbackNeverLeaksAcrossProjectScopesAndMissingProjectFailsClosed() {
    DatasetQueryPerformanceBuffer buffer = new DatasetQueryPerformanceBuffer();
    buffer.add(10L, trace("project-10", 1L, 10L));
    buffer.add(20L, trace("project-20", 1L, 20L));
    buffer.add(null, trace("legacy", 1L, 30L));

    DatasetQueryPerformanceReader scopedReader = scopedReader(buffer, 10L);
    DatasetQueryPerformanceReader missingProjectReader = new DatasetQueryPerformanceReader(buffer);

    var projectTraces = scopedReader.recent(Set.of(1L), 10);

    assertEquals(1, projectTraces.size());
    assertEquals("project-10", projectTraces.get(0).queryId());
    assertThrows(
        ProjectContextException.class,
        () -> missingProjectReader.recent(Set.of(1L), 10));
  }

  @Test
  void capsTheDiagnosticWindowAndQueryLimit() {
    Fixture fixture = fixture();
    for (int index = 0; index < DatasetQueryPerformanceBuffer.MAX_TRACES + 5; index++) {
      fixture.record(trace("q" + index, 1L, index));
    }

    var traces = fixture.reader.recent(Set.of(), DatasetQueryPerformanceReader.MAX_QUERY_LIMIT);

    assertEquals(DatasetQueryPerformanceReader.MAX_QUERY_LIMIT, traces.size());
    assertEquals("q504", traces.get(0).queryId());
  }

  private Fixture fixture() {
    DatasetQueryPerformanceBuffer buffer = new DatasetQueryPerformanceBuffer();
    return new Fixture(buffer, scopedReader(buffer, 7L));
  }

  private DatasetQueryPerformanceReader scopedReader(
      DatasetQueryPerformanceBuffer buffer, long projectId) {
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.current())
        .thenReturn(Optional.of(new ProjectContext(projectId, "p" + projectId)));
    return new DatasetQueryPerformanceReader(buffer, null, currentProject);
  }

  private static DatasetQueryPerformance trace(
      String queryId, long datasetId, long totalMillis) {
    return new DatasetQueryPerformance(
        queryId,
        datasetId,
        "dataset-" + datasetId,
        datasetId * 10,
        1,
        "SQL_QUERY",
        "ds-1",
        "select 1",
        1,
        2,
        3,
        4,
        totalMillis,
        1,
        false,
        Instant.parse("2026-08-18T10:00:00Z").plusMillis(totalMillis));
  }

  private static DatasetQueryPerformance failedTrace(
      String queryId, long datasetId, DatasetQueryStatus status, long totalMillis) {
    Instant startedAt = Instant.parse("2026-08-18T10:00:00Z").plusMillis(totalMillis);
    return new DatasetQueryPerformance(
        queryId,
        datasetId,
        "dataset-" + datasetId,
        datasetId * 10,
        1,
        "SQL_QUERY",
        "ds-1",
        "select * from t where id = 42",
        null,
        status,
        "EXECUTE_SOURCE",
        "RuntimeException",
        "boom",
        0L,
        1L,
        0L,
        0L,
        totalMillis,
        0,
        false,
        startedAt,
        startedAt.plusMillis(totalMillis));
  }

  private record Fixture(
      DatasetQueryPerformanceBuffer buffer, DatasetQueryPerformanceReader reader) {
    void record(DatasetQueryPerformance trace) {
      buffer.add(7L, trace);
    }
  }
}
