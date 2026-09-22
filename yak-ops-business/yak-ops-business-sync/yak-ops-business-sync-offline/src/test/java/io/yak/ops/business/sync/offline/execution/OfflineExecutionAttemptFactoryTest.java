package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineExecutionAttemptFactoryTest {

  @Test
  void createsAttemptFromFrozenBatchSnapshot() {
    OfflineExecutionAttemptFactory factory =
        new OfflineExecutionAttemptFactory(new OfflineSyncProperties());
    BatchExecution batch = new BatchExecution(
        77L,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot(
            "{\"definition\":\"frozen\"}",
            3,
            new RetryPolicySnapshot(3, 30),
            "frozen-digest",
            "{\"job\":\"batch-frozen\"}"),
        BatchStatus.WAITING_RETRY,
        List.of());

    OfflineJobExecution execution =
        factory.create(batch, 2, "RETRY", 99L, "offline-retry:77:2");

    assertThat(execution.getJobDefinitionId()).isEqualTo(10L);
    assertThat(execution.getBatchId()).isEqualTo(77L);
    assertThat(execution.getDefinitionVersion()).isEqualTo(3);
    assertThat(execution.getStatus()).isEqualTo("CREATED");
    assertThat(execution.getAttemptNo()).isEqualTo(2);
    assertThat(execution.getTriggerType()).isEqualTo("RETRY");
    assertThat(execution.getRetryFromExecutionId()).isEqualTo(99L);
    assertThat(execution.getIdempotencyKey()).isEqualTo("offline-retry:77:2");
    assertThat(execution.getConfigDigest()).isEqualTo("frozen-digest");
    assertThat(execution.getDefinitionSnapshotJson())
        .isEqualTo("{\"definition\":\"frozen\"}");
    assertThat(execution.getSubmittedConfig())
        .isEqualTo("{\"job\":\"batch-frozen\"}");
    assertThat(execution.getExternalExecutionId()).startsWith("yak-offline-");
  }
}
