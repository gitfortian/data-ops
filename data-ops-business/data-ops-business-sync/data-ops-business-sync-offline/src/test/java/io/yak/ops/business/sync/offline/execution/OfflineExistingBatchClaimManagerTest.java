package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineExistingBatchClaimManagerTest {

  @Mock private OfflineJobDefinitionRepository definitionRepository;
  @Mock private OfflineJobExecutionRepository executionRepository;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineBatchRuntime batchRuntime;

  private OfflineExistingBatchClaimManager manager;

  @BeforeEach
  void setUp() {
    manager = new OfflineExistingBatchClaimManager(
        definitionRepository,
        executionRepository,
        batchRepository,
        batchRuntime,
        new OfflineExecutionAttemptFactory(new OfflineSyncProperties()));
  }

  @Test
  void retryUsesFrozenBatchAndReservesBeforeAttemptInsert() {
    OfflineJobExecution previous = failedAttempt(99L, 77L, 1);
    BatchExecution batch = frozenBatch(77L, 3, BatchStatus.WAITING_RETRY);
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch));
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of(previous));
    when(executionRepository.reserveRetry(99L)).thenReturn(true);
    when(executionRepository.insert(any(OfflineJobExecution.class)))
        .thenAnswer(invocation -> {
          OfflineJobExecution execution = invocation.getArgument(0);
          execution.setId(100L);
          return true;
        });

    OfflineExecutionClaim result = manager.claimRetry(99L);
    OfflineJobExecution retry = result.getExecution();

    assertThat(result.getLogicalJobSpecJson()).isEqualTo("{\"job\":\"batch-frozen\"}");
    assertThat(retry.getBatchId()).isEqualTo(77L);
    assertThat(retry.getAttemptNo()).isEqualTo(2);
    assertThat(retry.getRetryFromExecutionId()).isEqualTo(99L);
    assertThat(retry.getTriggerType()).isEqualTo("RETRY");
    assertThat(retry.getIdempotencyKey()).isEqualTo("offline-retry:77:2");

    InOrder order = inOrder(executionRepository);
    order.verify(executionRepository).reserveRetry(99L);
    order.verify(executionRepository).insert(retry);
    verify(batchRuntime).refreshBatch(77L);
    verifyNoInteractions(definitionRepository);
  }

  @Test
  void pendingBackfillReservesBatchBeforeAttemptOneInsert() {
    BatchExecution pending = new BatchExecution(
        77L,
        10L,
        BatchKey.backfill(
            "bf-1", BatchScope.partitions(List.of("2026-08-01")).fingerprint()),
        BatchTrigger.BACKFILL,
        BatchScope.partitions(List.of("2026-08-01")),
        snapshot(3),
        BatchStatus.PENDING,
        List.of());
    when(batchRepository.findById(77L)).thenReturn(Optional.of(pending));
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of());
    when(batchRuntime.hasOccupyingBatch(10L)).thenReturn(false);
    when(batchRepository.reservePendingBackfill(77L)).thenReturn(true);
    when(executionRepository.insert(any(OfflineJobExecution.class)))
        .thenAnswer(invocation -> {
          OfflineJobExecution execution = invocation.getArgument(0);
          execution.setId(501L);
          return true;
        });

    OfflineExecutionClaim result = manager.claimPendingBackfill(77L);

    assertThat(result.getExecution().getAttemptNo()).isEqualTo(1);
    assertThat(result.getExecution().getTriggerType()).isEqualTo("BACKFILL");
    assertThat(result.getExecution().getIdempotencyKey()).isEqualTo("offline-backfill:77:1");
    InOrder order = inOrder(batchRepository, executionRepository);
    order.verify(batchRepository).reservePendingBackfill(77L);
    order.verify(executionRepository).insert(result.getExecution());
    verify(definitionRepository).lock(10L);
    verify(batchRuntime).refreshBatch(77L);
  }

  @Test
  void unknownAttemptCannotBlindRetry() {
    OfflineJobExecution previous = failedAttempt(99L, 77L, 1);
    previous.setStatus("UNKNOWN");
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));
    when(batchRepository.findById(77L))
        .thenReturn(Optional.of(frozenBatch(77L, 3, BatchStatus.UNKNOWN)));

    assertThatThrownBy(() -> manager.claimRetry(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNKNOWN")
        .hasMessageContaining("reconcile");

    verify(executionRepository, never()).reserveRetry(99L);
  }

  @Test
  void retryRespectsFrozenMaxAttempts() {
    OfflineJobExecution previous = failedAttempt(99L, 77L, 1);
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));
    when(batchRepository.findById(77L))
        .thenReturn(Optional.of(frozenBatch(77L, 1, BatchStatus.WAITING_RETRY)));
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of(previous));

    assertThatThrownBy(() -> manager.claimRetry(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("最大 Attempt");

    verify(executionRepository, never()).reserveRetry(99L);
  }

  @Test
  void legacyAttemptWithoutBatchIsHistoryOnly() {
    OfflineJobExecution previous = failedAttempt(99L, null, 1);
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));

    assertThatThrownBy(() -> manager.claimRetry(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未绑定 Batch")
        .hasMessageContaining("历史查询");
  }

  private OfflineJobExecution failedAttempt(Long id, Long batchId, int attemptNo) {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(id);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(batchId);
    execution.setAttemptNo(attemptNo);
    execution.setStatus("FAILED");
    execution.setRetryCreated(false);
    return execution;
  }

  private BatchExecution frozenBatch(long id, int maxAttempts, BatchStatus status) {
    return new BatchExecution(
        id,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        snapshot(maxAttempts),
        status,
        List.of());
  }

  private ExecutionSnapshot snapshot(int maxAttempts) {
    return new ExecutionSnapshot(
        "{\"definition\":\"frozen\"}",
        3,
        new RetryPolicySnapshot(maxAttempts, 30),
        "frozen-digest",
        "{\"job\":\"batch-frozen\"}");
  }
}
