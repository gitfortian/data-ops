package io.yak.ops.business.quality.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import org.junit.jupiter.api.Test;

class QualityScheduleCalculatorTest {
  private final QualityScheduleCalculator calculator = new QualityScheduleCalculator();

  @Test
  void shouldConvertFriendlySchedulesToQuartzCron() {
    assertEquals(
        "0 5 9 * * ?",
        calculator.cronExpression(ScheduleFrequency.DAILY, "09:05", null, null));
    assertEquals(
        "0 30 18 ? * FRI",
        calculator.cronExpression(
            ScheduleFrequency.WEEKLY, "18:30", ScheduleWeekday.FRI, null));
    assertEquals(
        "0 0 2 ? * *",
        calculator.cronExpression(
            ScheduleFrequency.CRON, null, null, "  0  0  2  *  *  *  "));
  }

  @Test
  void shouldRejectInvalidCron() {
    assertThrows(IllegalArgumentException.class, () -> calculator.validateCron("invalid cron"));
  }
}
