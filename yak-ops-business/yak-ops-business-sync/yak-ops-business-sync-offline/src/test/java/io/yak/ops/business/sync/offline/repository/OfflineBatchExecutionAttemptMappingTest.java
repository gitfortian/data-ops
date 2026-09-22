package io.yak.ops.business.sync.offline.repository;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.currentProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.AttemptReason;
import io.yak.ops.business.sync.offline.domain.core.AttemptStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.ExecutionAttempt;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineBatchExecutionAttemptMappingTest {

  @Test
  void lostPersistedExecutionHydratesAsUnknownRetryAttempt() {
    OfflineBatchExecutionDao dao = mock(OfflineBatchExecutionDao.class);
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    OfflineBatchExecutionRepositoryAdapter repository =
        new OfflineBatchExecutionRepositoryAdapter(dao, executions, currentProject());

    OfflineBatchExecutionPO batch = batch(77L);
    OfflineJobExecution persisted =
        OfflineJobExecution.builder()
            .id(20L)
            .jobDefinitionId(9L)
            .batchId(77L)
            .engineJobId("engine-job-20")
            .externalExecutionId("external-20")
            .idempotencyKey("idem-20")
            .workerInstanceId("worker-a")
            .status("LOST")
            .attemptNo(2)
            .triggerType("RETRY")
            .retryFromExecutionId(10L)
            .sourceRecordCount(100L)
            .sinkSuccessRecordCount(90L)
            .failedRecordCount(10L)
            .qps(25D)
            .durationMillis(5000L)
            .createTime(LocalDateTime.of(2026, 8, 23, 10, 0))
            .build();

    when(dao.selectById(77L)).thenReturn(batch);
    when(executions.findByBatchId(77L)).thenReturn(List.of(persisted));

    ExecutionAttempt attempt = repository.findById(77L).orElseThrow().attempts().get(0);

    assertThat(attempt.id()).isEqualTo(20L);
    assertThat(attempt.attemptNo()).isEqualTo(2);
    assertThat(attempt.reason()).isEqualTo(AttemptReason.RETRY);
    assertThat(attempt.status()).isEqualTo(AttemptStatus.UNKNOWN);
    assertThat(attempt.engineExecutionRef().jobId()).isEqualTo("engine-job-20");
    assertThat(attempt.metrics().sourceRecordCount()).isEqualTo(100L);
    assertThat(attempt.metrics().failedRecordCount()).isEqualTo(10L);
  }

  private OfflineBatchExecutionPO batch(long id) {
    BatchScope scope = BatchScope.fullSelection();
    OfflineBatchExecutionPO po = new OfflineBatchExecutionPO();
    po.setId(id);
    po.setJobDefinitionId(9L);
    po.setBatchKey("manual:test");
    po.setTriggerType("MANUAL");
    po.setBatchScopeType("FULL_SELECTION");
    po.setBatchScopeValue(scope.canonicalValue());
    po.setBatchScopeFingerprint(scope.fingerprint());
    po.setDefinitionSnapshotJson("{}");
    po.setDefinitionRevision(1);
    po.setRetryMaxAttempts(3);
    po.setRetryBackoffSeconds(30);
    po.setConfigDigest("digest");
    po.setLogicalJobSpecJson("{\"kind\":\"BatchSyncJob\"}");
    po.setStatus("RUNNING");
    return po;
  }
}
