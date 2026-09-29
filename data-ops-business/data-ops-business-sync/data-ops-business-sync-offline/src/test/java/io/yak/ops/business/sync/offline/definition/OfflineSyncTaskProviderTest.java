package io.yak.ops.business.sync.offline.definition;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import org.junit.jupiter.api.Test;

class OfflineSyncTaskProviderTest {

  @Test
  void shouldOnlyExposeOnlineTasksWithoutEnabledSchedule() {
    assertThat(OfflineSyncTaskProvider.isWorkflowEligible(task("ONLINE", false))).isTrue();
    assertThat(OfflineSyncTaskProvider.isWorkflowEligible(task("OFFLINE", false))).isFalse();
    assertThat(OfflineSyncTaskProvider.isWorkflowEligible(task("ONLINE", true))).isFalse();
  }

  @Test
  void shouldRejectIncompleteTaskMetadata() {
    assertThat(OfflineSyncTaskProvider.isWorkflowEligible(null)).isFalse();
    OfflineJobDefinition incomplete = new OfflineJobDefinition();
    incomplete.setJobName("未完成任务");
    incomplete.setReleaseState("ONLINE");
    incomplete.setScheduleEnabled(false);
    assertThat(OfflineSyncTaskProvider.isWorkflowEligible(incomplete)).isFalse();
  }

  private OfflineJobDefinition task(String releaseState, boolean scheduleEnabled) {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(1001L);
    definition.setJobName("用户数据同步");
    definition.setReleaseState(releaseState);
    definition.setScheduleEnabled(scheduleEnabled);
    return definition;
  }
}
