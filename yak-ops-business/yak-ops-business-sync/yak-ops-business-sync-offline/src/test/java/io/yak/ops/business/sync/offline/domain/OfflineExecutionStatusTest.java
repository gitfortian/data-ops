package io.yak.ops.business.sync.offline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OfflineExecutionStatusTest {

  @Test
  void shouldExposeOfflineAttemptLifecycleIncludingUnknown() {
    assertThat(OfflineExecutionStatus.CREATED.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.SUBMITTED.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.QUEUED.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.RUNNING.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.UNKNOWN.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.SUCCEEDED.isTerminal()).isTrue();
    assertThat(OfflineExecutionStatus.FAILED.isTerminal()).isTrue();
    assertThat(OfflineExecutionStatus.CANCELED.isTerminal()).isTrue();
    assertThat(OfflineExecutionStatus.UNKNOWN.isTerminal()).isFalse();
  }

  @Test
  void unknownOccupiesTheBatchSlotButIsNotConfirmedRemoteActivity() {
    assertThat(OfflineExecutionStatus.UNKNOWN.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.UNKNOWN.isConfirmedActive()).isFalse();
    assertThat(OfflineExecutionStatus.RUNNING.isConfirmedActive()).isTrue();
    assertThat(OfflineExecutionStatus.isConfirmedActive("RUNNING")).isTrue();
    assertThat(OfflineExecutionStatus.isConfirmedActive("UNKNOWN")).isFalse();
    assertThat(OfflineExecutionStatus.isConfirmedActive("LOST")).isFalse();
  }

  @Test
  void shouldTreatMissingPersistedStatusAsInactive() {
    assertThat(OfflineExecutionStatus.isActive(null)).isFalse();
    assertThat(OfflineExecutionStatus.isActive("")).isFalse();
    assertThat(OfflineExecutionStatus.isActive("   ")).isFalse();
  }

  @Test
  void legacyLostIsReadAliasOnlyAndNotADomainState() {
    assertThat(Arrays.stream(OfflineExecutionStatus.values()).map(Enum::name))
        .doesNotContain("LOST");
    assertThat(OfflineExecutionStatus.parse("FINISHED"))
        .isEqualTo(OfflineExecutionStatus.SUCCEEDED);
    assertThat(OfflineExecutionStatus.parse("CANCELLING"))
        .isEqualTo(OfflineExecutionStatus.CANCELED);
    assertThat(OfflineExecutionStatus.parse("LOST"))
        .isEqualTo(OfflineExecutionStatus.UNKNOWN);
    assertThat(OfflineExecutionStatus.isActive("LOST")).isTrue();
  }
}
