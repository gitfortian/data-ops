package io.yak.ops.business.quality.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.schedule.QualityScheduleCalculator;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class QualityMonitorSettingsPolicyTest {
  private final QualityMonitorSettingsPolicy policy =
      new QualityMonitorSettingsPolicy(new QualityScheduleCalculator());

  @Test
  void shouldUseCronAsCanonicalScheduleWhenEnabled() {
    var result = policy.normalize(
        settings(true, "0 0 9 ? * 2"),
        null);

    assertEquals(RunMode.SCHEDULE, result.runMode());
    assertEquals(ScheduleFrequency.CRON, result.scheduleFrequency());
    assertEquals("0 0 9 ? * 2", result.cronExpression());
    assertNull(result.scheduleTime());
    assertNull(result.scheduleWeekday());
  }

  @Test
  void shouldKeepCronWhenScheduleIsDisabled() {
    var result = policy.normalize(
        settings(false, "0 15 8 * * ?"),
        null);

    assertEquals(RunMode.MANUAL, result.runMode());
    assertEquals(ScheduleFrequency.CRON, result.scheduleFrequency());
    assertEquals("0 15 8 * * ?", result.cronExpression());
  }

  @Test
  void shouldPreserveLegacyFriendlyScheduleRequests() {
    var command = new QualityMonitorCommand.Settings(
        RunMode.SCHEDULE,
        ScheduleFrequency.WEEKLY,
        "18:30",
        ScheduleWeekday.FRI,
        null,
        RuleFailureAction.CONTINUE,
        false,
        NotifyChannel.MESSAGE,
        null,
        AlertLevel.WARNING,
        null);

    var result = policy.normalize(command, null);

    assertEquals(RunMode.SCHEDULE, result.runMode());
    assertEquals(ScheduleFrequency.WEEKLY, result.scheduleFrequency());
    assertEquals("18:30", result.scheduleTime());
    assertEquals(ScheduleWeekday.FRI, result.scheduleWeekday());
    assertNull(result.cronExpression());
  }

  @Test
  void shouldReuseExistingCronWhenCanonicalRequestOmitsCron() {
    var existing = new MonitorSettings(
        RunMode.MANUAL,
        ScheduleFrequency.CRON,
        null,
        null,
        "0 0 2 * * ?",
        LocalDateTime.now(),
        RuleFailureAction.CONTINUE,
        false,
        NotifyChannel.MESSAGE,
        null,
        AlertLevel.WARNING);

    var result = policy.normalize(settings(true, null), existing);

    assertEquals(RunMode.SCHEDULE, result.runMode());
    assertEquals("0 0 2 * * ?", result.cronExpression());
  }

  private QualityMonitorCommand.Settings settings(boolean enabled, String cron) {
    return new QualityMonitorCommand.Settings(
        null,
        null,
        null,
        null,
        cron,
        RuleFailureAction.CONTINUE,
        false,
        NotifyChannel.MESSAGE,
        null,
        AlertLevel.WARNING,
        enabled);
  }
}
