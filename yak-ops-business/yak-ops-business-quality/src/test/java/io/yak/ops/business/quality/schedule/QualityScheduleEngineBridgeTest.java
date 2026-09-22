package io.yak.ops.business.quality.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class QualityScheduleEngineBridgeTest {

  @Test
  void shouldMapQualityMonitorToYakScheduleDefinition() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    QualityScheduleEngineBridge bridge =
        new QualityScheduleEngineBridge(
            provider,
            new QualityScheduleCalculator(),
            currentProject(7L));

    ScheduleDefinition definition =
        bridge.toDefinition(
            monitor(true),
            settings(ScheduleFrequency.DAILY, "09:05", null, null));

    assertThat(definition.key().namespace()).isEqualTo("yak-ops-quality");
    assertThat(definition.key().name()).isEqualTo("42");
    assertThat(definition.trigger().expression()).isEqualTo("0 5 9 * * ?");
    assertThat(definition.trigger().zoneId()).isEqualTo(ZoneId.systemDefault());
    assertThat(definition.target().handler()).isEqualTo("qualityScheduleHandler");
    assertThat(definition.target().payload())
        .containsEntry("projectId", 7L)
        .containsEntry("monitorId", 42L);
    assertThat(definition.metadata())
        .containsEntry("projectId", "7")
        .containsEntry("monitorId", "42");
    assertThat(definition.policy().concurrencyPolicy())
        .isEqualTo(ConcurrencyPolicy.FORBID);
    assertThat(definition.policy().misfirePolicy())
        .isEqualTo(MisfirePolicy.FIRE_ONCE_NOW);
    assertThat(definition.policy().triggerRetries()).isZero();
    assertThat(definition.enabled()).isTrue();
  }

  @Test
  void shouldKeepDisabledMonitorDisabledInScheduleDefinition() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    QualityScheduleEngineBridge bridge =
        new QualityScheduleEngineBridge(
            provider,
            new QualityScheduleCalculator(),
            currentProject(7L));

    ScheduleDefinition definition =
        bridge.toDefinition(
            monitor(false),
            settings(
                ScheduleFrequency.WEEKLY,
                "18:30",
                ScheduleWeekday.FRI,
                null));

    assertThat(definition.trigger().expression()).isEqualTo("0 30 18 ? * FRI");
    assertThat(definition.enabled()).isFalse();
  }

  private CurrentProject currentProject(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "test-project"));
  }

  private Monitor monitor(boolean enabled) {
    return new Monitor(
        42L,
        "订单表质量检查",
        null,
        7L,
        "mysql-prod",
        "demo",
        null,
        "orders",
        null,
        "quality-owner",
        enabled,
        null,
        null,
        null,
        null,
        null,
        0,
        List.of());
  }

  private MonitorSettings settings(
      ScheduleFrequency frequency,
      String scheduleTime,
      ScheduleWeekday weekday,
      String cron) {
    return new MonitorSettings(
        RunMode.SCHEDULE,
        frequency,
        scheduleTime,
        weekday,
        cron,
        null,
        RuleFailureAction.CONTINUE,
        false,
        NotifyChannel.MESSAGE,
        null,
        AlertLevel.WARNING);
  }
}
