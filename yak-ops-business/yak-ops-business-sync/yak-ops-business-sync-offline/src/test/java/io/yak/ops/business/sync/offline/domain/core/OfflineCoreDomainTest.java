package io.yak.ops.business.sync.offline.domain.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineCoreDomainTest {

  @Test
  void scheduleBatchKeyUsesPlannedFireIdentity() {
    Instant planned = Instant.parse("2026-08-23T02:00:00Z");

    BatchKey first = BatchKey.schedule("task-1", planned);
    BatchKey replay = BatchKey.schedule("task-1", planned);
    BatchKey next = BatchKey.schedule("task-1", planned.plusSeconds(3600));

    assertThat(first).isEqualTo(replay);
    assertThat(first).isNotEqualTo(next);
  }

  @Test
  void partitionScopeNormalizesOrderAndDuplicates() {
    BatchScope.PartitionScope first =
        BatchScope.partitions(List.of(" dt=2026-08-22 ", "dt=2026-08-21", "dt=2026-08-22"));
    BatchScope.PartitionScope second =
        BatchScope.partitions(List.of("dt=2026-08-21", "dt=2026-08-22"));

    assertThat(first.partitions()).containsExactly("dt=2026-08-21", "dt=2026-08-22");
    assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
  }

  @Test
  void dataWindowRejectsEmptyOrReversedWindow() {
    LocalDateTime start = LocalDateTime.of(2026, 8, 23, 0, 0);

    assertThatThrownBy(() -> BatchScope.dataWindow(start, start))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BatchScope.dataWindow(start, start.minusDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void batchRequiresSequentialAttemptNumbers() {
    ExecutionSnapshot snapshot =
        new ExecutionSnapshot(
            "definition",
            3,
            new RetryPolicySnapshot(3, 60),
            "digest",
            "{\"kind\":\"BatchSyncJob\"}");
    ExecutionAttempt first = attempt(1);
    ExecutionAttempt third = attempt(3);

    assertThatThrownBy(
            () ->
                new BatchExecution(
                    null,
                    1L,
                    BatchKey.manual("request-1"),
                    BatchTrigger.MANUAL,
                    BatchScope.fullSelection(),
                    snapshot,
                    BatchStatus.RUNNING,
                    List.of(first, third)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AttemptNo");
  }

  @Test
  void executionSnapshotOwnsFrozenLogicalJobSpec() {
    ExecutionSnapshot snapshot = new ExecutionSnapshot(
        "definition",
        2,
        new RetryPolicySnapshot(2, 10),
        "digest",
        "{\"source\":{\"connectorId\":\"jdbc\"}}");

    assertThat(snapshot.logicalJobSpec()).contains("jdbc");
  }

  @Test
  void unknownOccupiesExecutionSlotAndIsNotTerminal() {
    assertThat(AttemptStatus.UNKNOWN.isTerminal()).isFalse();
    assertThat(AttemptStatus.UNKNOWN.blocksNextAttempt()).isTrue();
    assertThat(BatchStatus.UNKNOWN.isTerminal()).isFalse();
    assertThat(BatchStatus.UNKNOWN.occupiesTaskExecutionSlot()).isTrue();
  }

  private ExecutionAttempt attempt(int attemptNo) {
    return new ExecutionAttempt(
        null,
        attemptNo,
        attemptNo == 1 ? AttemptReason.INITIAL : AttemptReason.RETRY,
        "idem-" + attemptNo,
        "ext-" + attemptNo,
        AttemptStatus.RUNNING,
        null,
        AttemptMetrics.empty(),
        null,
        LocalDateTime.of(2026, 8, 23, 10, 0),
        null,
        null);
  }
}
