package io.yak.ops.business.dataset.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.repository.DatasetQueryPerformanceStore;
import io.yak.ops.core.project.CurrentProject;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DatasetQueryPerformanceRecorderTest {

  @Test
  @SuppressWarnings("unchecked")
  void persistenceFailureFallsBackLocallyWithoutThrowing() {
    DatasetQueryPerformanceBuffer buffer = new DatasetQueryPerformanceBuffer();
    DatasetQueryPerformanceStore store = mock(DatasetQueryPerformanceStore.class);
    ObjectProvider<DatasetQueryPerformanceStore> provider = mock(ObjectProvider.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(provider.getIfAvailable()).thenReturn(store);
    when(currentProject.current()).thenReturn(Optional.empty());
    doThrow(new IllegalStateException("diagnostic db unavailable"))
        .when(store).append(any(), any());

    DatasetQueryPerformanceRecorder recorder = new DatasetQueryPerformanceRecorder(
        buffer,
        provider,
        currentProject,
        new DatasetQuerySqlEvidence(),
        new DatasetQueryObservabilityProperties());

    assertDoesNotThrow(() -> recorder.record(new DatasetQueryPerformance(
        "q1", 7L, "patients", 9L, 1, "SQL_QUERY", "ds-1",
        "select * from patient where name = 'Alice'", 0L, 1L, 2L, 0L, 3L,
        1, false, Instant.EPOCH)));

    var traces = new DatasetQueryPerformanceReader(buffer).recent(java.util.Set.of(7L), 10);
    assertEquals(1, traces.size());
    assertFalse(traces.get(0).sql().contains("Alice"));
  }
}
