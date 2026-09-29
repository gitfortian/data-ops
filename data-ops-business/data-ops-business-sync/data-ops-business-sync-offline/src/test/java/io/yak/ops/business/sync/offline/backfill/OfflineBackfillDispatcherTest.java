package io.yak.ops.business.sync.offline.backfill;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.PROJECT_ID;
import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.directScope;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository.ProjectBatchRef;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflineBackfillDispatcherTest {

  @Test
  void cursorRangeDispatchesOnlyWhenCursorMatchesAfterExclusive() {
    OfflineBatchExecutionRepository batches = Mockito.mock(OfflineBatchExecutionRepository.class);
    OfflineCursorGateway cursors = Mockito.mock(OfflineCursorGateway.class);
    OfflineJobExecutionService executionService = Mockito.mock(OfflineJobExecutionService.class);
    OfflineBackfillDispatcher dispatcher =
        new OfflineBackfillDispatcher(
            batches, cursors, executionService, new OfflineSyncProperties(), directScope());
    BatchExecution pending = pendingCursor("100", "200");

    when(batches.findPendingBackfillsForDispatch(100))
        .thenReturn(List.of(new ProjectBatchRef(PROJECT_ID, 77L)));
    when(batches.findById(77L)).thenReturn(Optional.of(pending));
    when(executionService.hasOccupyingBatch(10L)).thenReturn(false);
    when(cursors.find(10L, "orders"))
        .thenReturn(
            Optional.of(new OfflineSyncCursor(10L, "orders", "updated_at", "100", null, 1L)));

    dispatcher.dispatch();

    verify(executionService).executePendingBackfill(77L);
  }

  @Test
  void nextCursorRangeStaysPendingUntilPredecessorAdvancesCursor() {
    OfflineBatchExecutionRepository batches = Mockito.mock(OfflineBatchExecutionRepository.class);
    OfflineCursorGateway cursors = Mockito.mock(OfflineCursorGateway.class);
    OfflineJobExecutionService executionService = Mockito.mock(OfflineJobExecutionService.class);
    OfflineBackfillDispatcher dispatcher =
        new OfflineBackfillDispatcher(
            batches, cursors, executionService, new OfflineSyncProperties(), directScope());
    BatchExecution pending = pendingCursor("200", "300");

    when(batches.findPendingBackfillsForDispatch(100))
        .thenReturn(List.of(new ProjectBatchRef(PROJECT_ID, 77L)));
    when(batches.findById(77L)).thenReturn(Optional.of(pending));
    when(executionService.hasOccupyingBatch(10L)).thenReturn(false);
    when(cursors.find(10L, "orders"))
        .thenReturn(
            Optional.of(new OfflineSyncCursor(10L, "orders", "updated_at", "100", null, 1L)));

    dispatcher.dispatch();

    verify(executionService, never()).executePendingBackfill(77L);
  }

  private BatchExecution pendingCursor(String after, String through) {
    BatchScope.CursorRange scope = BatchScope.cursorRange("orders", after, through);
    return new BatchExecution(
        77L,
        10L,
        BatchKey.backfill("bf", scope.fingerprint()),
        BatchTrigger.BACKFILL,
        scope,
        new ExecutionSnapshot(
            "{}",
            1,
            new RetryPolicySnapshot(2, 10),
            "digest",
            "{\"kind\":\"BatchSyncJob\"}"),
        BatchStatus.PENDING,
        List.of());
  }
}
