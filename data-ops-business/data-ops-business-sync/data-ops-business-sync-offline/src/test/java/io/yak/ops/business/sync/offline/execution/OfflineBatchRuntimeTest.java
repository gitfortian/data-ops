package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.AttemptMetrics;
import io.yak.ops.business.sync.offline.domain.core.AttemptReason;
import io.yak.ops.business.sync.offline.domain.core.AttemptStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionAttempt;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class OfflineBatchRuntimeTest {

  @Test
  void latestAttemptIsTheOnlySourceOfBatchRuntimeTruth() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineCursorGateway cursors = mock(OfflineCursorGateway.class);
    OfflineBatchRuntime runtime = new OfflineBatchRuntime(batches, attempts, cursors);
    BatchExecution batch = batch(77L, BatchStatus.PENDING, List.of(), BatchScope.fullSelection());
    OfflineJobExecution oldSucceeded = attempt(501L, 77L, 1, "SUCCEEDED");
    OfflineJobExecution latestRunning = attempt(502L, 77L, 2, "RUNNING");
    when(batches.findById(77L)).thenReturn(Optional.of(batch));
    when(attempts.findByBatchId(77L)).thenReturn(List.of(oldSucceeded, latestRunning));
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    runtime.refreshBatch(77L);

    ArgumentCaptor<BatchExecution> captured = ArgumentCaptor.forClass(BatchExecution.class);
    verify(batches).update(captured.capture());
    assertThat(captured.getValue().status()).isEqualTo(BatchStatus.RUNNING);
  }

  @Test
  void failedAttemptWithRetryReservationWindowMeansWaitingRetry() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineBatchRuntime runtime =
        new OfflineBatchRuntime(batches, attempts, mock(OfflineCursorGateway.class));
    BatchExecution batch = batch(77L, BatchStatus.RUNNING, List.of(), BatchScope.fullSelection());
    OfflineJobExecution failed = attempt(501L, 77L, 1, "FAILED");
    failed.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
    when(batches.findById(77L)).thenReturn(Optional.of(batch));
    when(attempts.findByBatchId(77L)).thenReturn(List.of(failed));
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    runtime.refreshBatch(77L);

    ArgumentCaptor<BatchExecution> captured = ArgumentCaptor.forClass(BatchExecution.class);
    verify(batches).update(captured.capture());
    assertThat(captured.getValue().status()).isEqualTo(BatchStatus.WAITING_RETRY);
  }

  @Test
  void persistsAttemptBeforeRefreshingBatchTruth() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineBatchRuntime runtime =
        new OfflineBatchRuntime(batches, attempts, mock(OfflineCursorGateway.class));
    BatchExecution batch = batch(77L, BatchStatus.PENDING, List.of(), BatchScope.fullSelection());
    OfflineJobExecution running = attempt(501L, 77L, 1, "RUNNING");
    when(attempts.update(running)).thenReturn(true);
    when(batches.findById(77L)).thenReturn(Optional.of(batch));
    when(attempts.findByBatchId(77L)).thenReturn(List.of(running));
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    runtime.persistAttempt(running);

    InOrder order = inOrder(attempts, batches);
    order.verify(attempts).update(running);
    order.verify(batches).findById(77L);
    order.verify(attempts).findByBatchId(77L);
    order.verify(batches).update(any(BatchExecution.class));
  }

  @Test
  void succeededCursorBatchAdvancesCursorOnlyAfterBatchStatusIsPersisted() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineCursorGateway cursors = mock(OfflineCursorGateway.class);
    OfflineBatchRuntime runtime = new OfflineBatchRuntime(batches, attempts, cursors);
    BatchScope.CursorRange range = BatchScope.cursorRange("orders-cursor", "100", "200");
    BatchExecution batch = batch(77L, BatchStatus.RUNNING, List.of(), range);
    OfflineJobExecution succeeded = attempt(501L, 77L, 1, "SUCCEEDED");
    when(batches.findById(77L)).thenReturn(Optional.of(batch));
    when(attempts.findByBatchId(77L)).thenReturn(List.of(succeeded));
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    runtime.refreshBatch(77L);

    InOrder order = inOrder(batches, cursors);
    order.verify(batches).update(any(BatchExecution.class));
    order.verify(cursors).advanceAfterSucceededBatch(any(BatchExecution.class));
  }

  @Test
  void unknownAttemptKeepsBatchInUnknownInsteadOfFailed() {
    OfflineBatchRuntime runtime =
        new OfflineBatchRuntime(
            mock(OfflineBatchExecutionRepository.class),
            mock(OfflineJobExecutionRepository.class),
            mock(OfflineCursorGateway.class));
    assertThat(runtime.deriveStatus(attempt(501L, 77L, 1, "UNKNOWN")))
        .isEqualTo(BatchStatus.UNKNOWN);
  }

  @Test
  void persistedLostCompatibilityValueAlsoMapsToUnknown() {
    OfflineBatchRuntime runtime =
        new OfflineBatchRuntime(
            mock(OfflineBatchExecutionRepository.class),
            mock(OfflineJobExecutionRepository.class),
            mock(OfflineCursorGateway.class));
    assertThat(runtime.deriveStatus(attempt(501L, 77L, 1, "LOST")))
        .isEqualTo(BatchStatus.UNKNOWN);
  }

  @Test
  void cancelWaitingRetryUsesSameCasReservationAsAutomaticRetry() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineBatchRuntime runtime =
        new OfflineBatchRuntime(batches, attempts, mock(OfflineCursorGateway.class));
    OfflineJobExecution failed = attempt(501L, 77L, 1, "FAILED");
    failed.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
    BatchExecution waiting =
        batch(
            77L,
            BatchStatus.WAITING_RETRY,
            List.of(evidence(failed)),
            BatchScope.fullSelection());
    when(attempts.findById(501L)).thenReturn(Optional.of(failed));
    when(attempts.reserveRetry(501L)).thenReturn(true);
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    OfflineJobExecution result = runtime.cancelWaitingRetry(waiting);

    assertThat(result).isSameAs(failed);
    assertThat(failed.getNextRetryTime()).isNull();
    assertThat(failed.getRetryCreated()).isTrue();
    verify(attempts).reserveRetry(501L);
    ArgumentCaptor<BatchExecution> captured = ArgumentCaptor.forClass(BatchExecution.class);
    verify(batches).update(captured.capture());
    assertThat(captured.getValue().status()).isEqualTo(BatchStatus.CANCELED);
  }

  private ExecutionAttempt evidence(OfflineJobExecution execution) {
    return new ExecutionAttempt(
        execution.getId(),
        execution.getAttemptNo(),
        execution.getAttemptNo() > 1 ? AttemptReason.RETRY : AttemptReason.INITIAL,
        execution.getIdempotencyKey(),
        execution.getExternalExecutionId(),
        AttemptStatus.valueOf(execution.getStatus()),
        null,
        AttemptMetrics.empty(),
        execution.getErrorMessage(),
        execution.getCreateTime(),
        execution.getStartTime(),
        execution.getEndTime());
  }

  private BatchExecution batch(
      long id, BatchStatus status, List<ExecutionAttempt> attempts, BatchScope scope) {
    return new BatchExecution(
        id,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        scope,
        new ExecutionSnapshot(
            "{}",
            1,
            new RetryPolicySnapshot(3, 30),
            "digest",
            "{\"kind\":\"BatchSyncJob\"}"),
        status,
        attempts);
  }

  private OfflineJobExecution attempt(long id, long batchId, int attemptNo, String status) {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(id);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(batchId);
    execution.setAttemptNo(attemptNo);
    execution.setTriggerType(attemptNo == 1 ? "MANUAL" : "RETRY");
    execution.setIdempotencyKey("attempt-" + id);
    execution.setExternalExecutionId("external-" + id);
    execution.setStatus(status);
    execution.setRetryCreated(false);
    execution.setCreateTime(LocalDateTime.of(2026, 8, 23, 10, attemptNo));
    return execution;
  }
}
