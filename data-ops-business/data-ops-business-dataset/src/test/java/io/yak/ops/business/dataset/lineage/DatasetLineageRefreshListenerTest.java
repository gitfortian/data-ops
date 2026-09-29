package io.yak.ops.business.dataset.lineage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DatasetLineageRefreshListenerTest {

  @Test
  void refreshRestoresProjectBeforeLoadingCommittedSnapshotAndSynchronizingLineage() {
    DatasetLineageSnapshotReader snapshotReader = mock(DatasetLineageSnapshotReader.class);
    DatasetLineageTransactionRunner transactionRunner =
        mock(DatasetLineageTransactionRunner.class);
    RecordingProjectScope projectScope = new RecordingProjectScope();
    DatasetLineageRefreshListener listener =
        new DatasetLineageRefreshListener(snapshotReader, transactionRunner, projectScope);

    Dataset dataset =
        new Dataset(
            21L, 7L, "sales", null, DatasetStatus.ONLINE, null, Instant.EPOCH, Instant.EPOCH);
    DatasetDetail detail = new DatasetDetail(dataset, null, List.of(), List.of());
    when(snapshotReader.require(21L)).thenReturn(detail);

    listener.refresh(new DatasetLineageRefreshRequested(7L, 21L));

    assertEquals(7L, projectScope.lastProjectId);
    verify(snapshotReader).require(21L);
    verify(transactionRunner).sync(detail);
  }

  @Test
  void lineageFailureDoesNotEscapeAfterDatasetCommit() {
    DatasetLineageSnapshotReader snapshotReader = mock(DatasetLineageSnapshotReader.class);
    DatasetLineageTransactionRunner transactionRunner =
        mock(DatasetLineageTransactionRunner.class);
    DatasetLineageRefreshListener listener =
        new DatasetLineageRefreshListener(
            snapshotReader, transactionRunner, new RecordingProjectScope());
    when(snapshotReader.require(21L)).thenThrow(new IllegalStateException("lineage read failed"));

    assertDoesNotThrow(
        () -> listener.refresh(new DatasetLineageRefreshRequested(7L, 21L)));
  }

  private static final class RecordingProjectScope implements ProjectContextScope {
    private Long lastProjectId;

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      lastProjectId = context.projectId();
      return action.get();
    }
  }
}
