package io.yak.ops.business.sync.offline.repository;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.currentProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.AttemptStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineBatchExecutionRepositoryAdapterTest {

  @Test
  void roundTripsBatchIdentityScopeAndFrozenSnapshot() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    when(executions.findByBatchId(anyLong())).thenReturn(List.of());
    OfflineBatchExecutionRepositoryAdapter repository = repository(dao, executions);

    BatchExecution source = new BatchExecution(
        null,
        42L,
        BatchKey.schedule("schedule-42", Instant.parse("2026-08-23T02:00:00Z")),
        BatchTrigger.SCHEDULE,
        BatchScope.partitions(List.of("dt=2026-08-23", "dt=2026-08-22", "dt=2026-08-23")),
        snapshot(7, 3),
        BatchStatus.PENDING,
        List.of());

    BatchExecution inserted = repository.insert(source);
    BatchExecution reloaded = repository.findById(inserted.id()).orElseThrow();

    assertThat(inserted.id()).isEqualTo(101L);
    assertThat(reloaded.taskId()).isEqualTo(42L);
    assertThat(reloaded.batchKey()).isEqualTo(source.batchKey());
    assertThat(reloaded.trigger()).isEqualTo(BatchTrigger.SCHEDULE);
    assertThat(reloaded.batchScope()).isEqualTo(source.batchScope());
    assertThat(reloaded.batchScope().fingerprint()).isEqualTo(source.batchScope().fingerprint());
    assertThat(reloaded.snapshot()).isEqualTo(source.snapshot());
    assertThat(reloaded.snapshot().logicalJobSpec()).contains("BatchSyncJob");
    assertThat(reloaded.status()).isEqualTo(BatchStatus.PENDING);
    assertThat(reloaded.attempts()).isEmpty();
    assertThat(repository.findByTaskIdAndBatchKey(42L, source.batchKey())).contains(reloaded);
  }

  @Test
  void exposesOnlyOccupyingBatchAsRuntimeTruth() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    when(executions.findByBatchId(anyLong())).thenReturn(List.of());
    OfflineBatchExecutionRepositoryAdapter repository = repository(dao, executions);

    BatchExecution running = repository.insert(new BatchExecution(
        null,
        9L,
        BatchKey.manual("request-9"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        snapshot(1, 2),
        BatchStatus.RUNNING,
        List.of()));

    assertThat(repository.hasOccupyingBatch(9L)).isTrue();
    assertThat(repository.findLatestOccupyingByTaskId(9L)).contains(running);

    repository.update(new BatchExecution(
        running.id(),
        running.taskId(),
        running.batchKey(),
        running.trigger(),
        running.batchScope(),
        running.snapshot(),
        BatchStatus.SUCCEEDED,
        running.attempts()));

    assertThat(repository.hasOccupyingBatch(9L)).isFalse();
    assertThat(repository.findLatestOccupyingByTaskId(9L)).isEmpty();
  }

  @Test
  void pendingBackfillUsesCasReservationBeforeAttemptCreation() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    when(executions.findByBatchId(anyLong())).thenReturn(List.of());
    OfflineBatchExecutionRepositoryAdapter repository = repository(dao, executions);

    BatchExecution pending = repository.insert(new BatchExecution(
        null,
        9L,
        BatchKey.backfill("bf-1", BatchScope.fullSelection().fingerprint()),
        BatchTrigger.BACKFILL,
        BatchScope.fullSelection(),
        snapshot(1, 2),
        BatchStatus.PENDING,
        List.of()));

    assertThat(repository.findPendingBackfills(10)).containsExactly(pending);
    assertThat(repository.reservePendingBackfill(pending.id())).isTrue();
    assertThat(repository.reservePendingBackfill(pending.id())).isFalse();
    assertThat(repository.findPendingBackfills(10)).isEmpty();
  }

  @Test
  void hydratesBoundLegacyExecutionsAsAttempts() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    OfflineBatchExecutionRepositoryAdapter repository = repository(dao, executions);

    BatchExecution inserted = repository.insert(new BatchExecution(
        null,
        9L,
        BatchKey.manual("request-9"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        snapshot(1, 2),
        BatchStatus.RUNNING,
        List.of()));

    OfflineJobExecution legacyAttempt = OfflineJobExecution.builder()
        .id(501L)
        .jobDefinitionId(9L)
        .batchId(inserted.id())
        .attemptNo(1)
        .triggerType("MANUAL")
        .idempotencyKey("attempt-501")
        .externalExecutionId("external-501")
        .status("RUNNING")
        .createTime(LocalDateTime.of(2026, 8, 23, 10, 0))
        .build();
    when(executions.findByBatchId(inserted.id())).thenReturn(List.of(legacyAttempt));

    BatchExecution reloaded = repository.findById(inserted.id()).orElseThrow();

    assertThat(reloaded.attempts()).hasSize(1);
    assertThat(reloaded.attempts().get(0).id()).isEqualTo(501L);
    assertThat(reloaded.attempts().get(0).attemptNo()).isEqualTo(1);
    assertThat(reloaded.attempts().get(0).status()).isEqualTo(AttemptStatus.RUNNING);
  }

  @Test
  void missingBatchLogicalJobSpecDoesNotFallBackToAttemptCopy() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    OfflineBatchExecutionRepositoryAdapter repository = repository(dao, executions);

    BatchExecution inserted = repository.insert(new BatchExecution(
        null,
        9L,
        BatchKey.manual("request-9"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        snapshot(1, 2),
        BatchStatus.RUNNING,
        List.of()));
    dao.stored.setLogicalJobSpecJson(null);

    OfflineJobExecution compatibilityCopy = OfflineJobExecution.builder()
        .id(501L)
        .jobDefinitionId(9L)
        .batchId(inserted.id())
        .attemptNo(1)
        .triggerType("MANUAL")
        .idempotencyKey("attempt-501")
        .externalExecutionId("external-501")
        .submittedConfig("{\"kind\":\"BatchSyncJob\"}")
        .status("RUNNING")
        .createTime(LocalDateTime.of(2026, 8, 23, 10, 0))
        .build();
    when(executions.findByBatchId(inserted.id())).thenReturn(List.of(compatibilityCopy));

    assertThatThrownBy(() -> repository.findById(inserted.id()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("logicalJobSpec");
  }

  @Test
  void rejectsCorruptedScopeEvidence() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    when(executions.findByBatchId(anyLong())).thenReturn(List.of());
    OfflineBatchExecutionRepositoryAdapter repository = repository(dao, executions);

    BatchExecution source = new BatchExecution(
        null,
        7L,
        BatchKey.manual("request-7"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        snapshot(1, 1),
        BatchStatus.PENDING,
        List.of());

    BatchExecution inserted = repository.insert(source);
    dao.stored.setBatchScopeFingerprint("corrupted");

    assertThatThrownBy(() -> repository.findById(inserted.id()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fingerprint");
  }

  private OfflineBatchExecutionRepositoryAdapter repository(
      OfflineBatchExecutionDao dao, OfflineJobExecutionRepository executions) {
    return new OfflineBatchExecutionRepositoryAdapter(dao, executions, currentProject());
  }

  private ExecutionSnapshot snapshot(int revision, int maxAttempts) {
    return new ExecutionSnapshot(
        "{\"source\":\"orders\"}",
        revision,
        new RetryPolicySnapshot(maxAttempts, 30),
        "digest-" + revision,
        "{\"kind\":\"BatchSyncJob\",\"source\":{\"connectorId\":\"jdbc\"}}");
  }

  private static final class FakeBatchDao implements OfflineBatchExecutionDao {
    private OfflineBatchExecutionPO stored;

    @Override
    public OfflineBatchExecutionPO selectById(Long id) {
      return stored != null && stored.getId().equals(id) ? stored : null;
    }

    @Override
    public OfflineBatchExecutionPO selectByTaskIdAndBatchKey(Long taskId, String batchKey) {
      return stored != null
              && stored.getJobDefinitionId().equals(taskId)
              && stored.getBatchKey().equals(batchKey)
          ? stored
          : null;
    }

    @Override
    public boolean existsByTaskIdAndStatuses(Long taskId, List<String> statuses) {
      return stored != null
          && stored.getJobDefinitionId().equals(taskId)
          && statuses.contains(stored.getStatus());
    }

    @Override
    public OfflineBatchExecutionPO selectLatestByTaskIdAndStatuses(
        Long taskId,
        List<String> statuses) {
      return existsByTaskIdAndStatuses(taskId, statuses) ? stored : null;
    }

    @Override
    public List<OfflineBatchExecutionPO> selectPendingBackfills(int limit) {
      return stored != null
              && "BACKFILL".equals(stored.getTriggerType())
              && "PENDING".equals(stored.getStatus())
          ? List.of(stored)
          : List.of();
    }

    @Override
    public List<OfflineBatchExecutionPO> selectPendingBackfillsForDispatch(int limit) {
      return selectPendingBackfills(limit);
    }

    @Override
    public boolean reservePendingBackfill(Long batchId, LocalDateTime updateTime) {
      if (stored == null
          || !stored.getId().equals(batchId)
          || !"BACKFILL".equals(stored.getTriggerType())
          || !"PENDING".equals(stored.getStatus())) {
        return false;
      }
      stored.setStatus("RUNNING");
      stored.setUpdateTime(updateTime);
      return true;
    }

    @Override
    public boolean updateAuditCarrier(Long batchId, String carrierJson, LocalDateTime updateTime) {
      if (stored == null || !stored.getId().equals(batchId)) {
        return false;
      }
      stored.setAuditCarrierJson(carrierJson);
      stored.setUpdateTime(updateTime);
      return true;
    }

    @Override
    public boolean insert(OfflineBatchExecutionPO batchPO) {
      batchPO.setId(101L);
      stored = batchPO;
      return true;
    }

    @Override
    public boolean updateById(OfflineBatchExecutionPO batchPO) {
      stored = batchPO;
      return true;
    }
  }
}
