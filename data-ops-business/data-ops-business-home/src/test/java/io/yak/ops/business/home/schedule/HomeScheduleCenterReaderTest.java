package io.yak.ops.business.home.schedule;

import static io.yak.ops.common.schedule.YakScheduleNamespaces.DATA_QUALITY;
import static io.yak.ops.common.schedule.YakScheduleNamespaces.OFFLINE_SYNC;
import static io.yak.ops.common.schedule.YakScheduleNamespaces.WORKFLOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HomeScheduleCenterReaderTest {

  @Test
  void shouldBuildCalendarFromUnifiedScheduleSnapshots() {
    ScheduleManager manager = mock(ScheduleManager.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(manager);
    when(manager.list(OFFLINE_SYNC)).thenReturn(List.of(snapshot(
        OFFLINE_SYNC, "11", "订单离线同步", "0 0 9 * * ?", ScheduleStatus.ENABLED, Map.of())));
    when(manager.list(WORKFLOW)).thenReturn(List.of(snapshot(
        WORKFLOW, "workflow-schedule-1", "每日加工工作流", "0 30 10 * * ?", ScheduleStatus.ENABLED,
        Map.of("startTime", "2026-08-10T00:00:00Z"))));
    when(manager.list(DATA_QUALITY)).thenReturn(List.of(snapshot(
        DATA_QUALITY, "21", "订单完整性检查", "0 0 11 * * ?", ScheduleStatus.ENABLED, Map.of())));

    HomeScheduleCenterReader reader = new HomeScheduleCenterReader(provider);
    HomeScheduleCenterReader.CalendarResponse response = reader.calendar("2026-08");

    assertThat(response.totalSchedules()).isEqualTo(3);
    assertThat(response.days()).isNotEmpty();
    assertThat(response.overview()).hasSize(3);
    assertThat(response.overview()).extracting(HomeScheduleCenterReader.ScheduleSummary::taskType)
        .containsExactlyInAnyOrder("OFFLINE_SYNC", "WORKFLOW", "DATA_QUALITY");
    assertThat(response.overview()).extracting(HomeScheduleCenterReader.ScheduleSummary::scheduleText)
        .contains("0 0 9 * * ?", "0 30 10 * * ?", "0 0 11 * * ?");
    verify(manager).list(OFFLINE_SYNC);
    verify(manager).list(WORKFLOW);
    verify(manager).list(DATA_QUALITY);
  }

  @Test
  void shouldIgnorePausedSchedules() {
    ScheduleManager manager = mock(ScheduleManager.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(manager);
    when(manager.list(OFFLINE_SYNC)).thenReturn(List.of(snapshot(
        OFFLINE_SYNC, "11", "已暂停任务", "0 0 9 * * ?", ScheduleStatus.PAUSED, Map.of())));
    when(manager.list(WORKFLOW)).thenReturn(List.of());
    when(manager.list(DATA_QUALITY)).thenReturn(List.of());

    HomeScheduleCenterReader.CalendarResponse response =
        new HomeScheduleCenterReader(provider).calendar("2026-08");

    assertThat(response.totalSchedules()).isZero();
    assertThat(response.days()).isEmpty();
    assertThat(response.overview()).isEmpty();
  }

  private ScheduleSnapshot snapshot(
      String namespace,
      String id,
      String name,
      String cron,
      ScheduleStatus status,
      Map<String, String> metadata) {
    ScheduleDefinition definition = new ScheduleDefinition(
        new ScheduleKey(namespace, id),
        name,
        ScheduleTrigger.cron(cron, ZoneId.of("Asia/Shanghai")),
        new ScheduleTarget("testHandler", Map.of("id", id)),
        SchedulePolicy.defaults(),
        true,
        metadata);
    return new ScheduleSnapshot(
        definition,
        "quartz",
        namespace + "/" + id,
        status,
        Instant.parse("2026-08-18T01:00:00Z"),
        null);
  }
}
