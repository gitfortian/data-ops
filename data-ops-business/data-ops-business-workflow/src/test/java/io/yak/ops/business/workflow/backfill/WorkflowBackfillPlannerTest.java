package io.yak.ops.business.workflow.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WorkflowBackfillPlannerTest {
  private final WorkflowBackfillPlanner planner = new WorkflowBackfillPlanner();

  @Test
  void shouldGenerateHistoricalOccurrencesInScheduleTimezone() {
    var plan = planner.plan(
        "0 0 2 * * ?",
        "Asia/Shanghai",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 3));

    assertThat(plan.timezone()).isEqualTo("Asia/Shanghai");
    assertThat(plan.occurrences()).hasSize(3);
    assertThat(plan.occurrences().get(0).businessDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(plan.occurrences().get(0).scheduleInstant())
        .isEqualTo(Instant.parse("2026-07-31T18:00:00Z"));
    assertThat(plan.occurrences().get(0).scheduleTime())
        .isEqualTo("2026-08-01T02:00:00+08:00");
    assertThat(plan.occurrences().get(2).businessDate()).isEqualTo(LocalDate.of(2026, 8, 3));
  }

  @Test
  void shouldNormalizeFiveFieldCronToQuartzDaySemantics() {
    var plan = planner.plan(
        "0 2 * * *",
        "Asia/Shanghai",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 1));

    assertThat(plan.cronExpression()).isEqualTo("0 0 2 * * ?");
    assertThat(plan.occurrences()).hasSize(1);
  }

  @Test
  void shouldKeepStandardFiveFieldNumericWeekdaySemantics() {
    var plan = planner.plan(
        "0 2 * * 0",
        "UTC",
        LocalDate.of(2026, 8, 2),
        LocalDate.of(2026, 8, 2));

    assertThat(plan.occurrences()).hasSize(1);
    assertThat(plan.occurrences().get(0).businessDate()).isEqualTo(LocalDate.of(2026, 8, 2));
  }

  @Test
  void shouldTranslateQuartzNumericWeekdayToSpringSemantics() {
    var plan = planner.plan(
        "0 0 2 ? * 1",
        "UTC",
        LocalDate.of(2026, 8, 2),
        LocalDate.of(2026, 8, 2));

    assertThat(plan.occurrences()).hasSize(1);
    assertThat(plan.occurrences().get(0).businessDate()).isEqualTo(LocalDate.of(2026, 8, 2));
  }

  @Test
  void shouldApplyQuartzYearFieldWithoutQuartzDependency() {
    var plan = planner.plan(
        "0 0 2 * * ? 2027",
        "UTC",
        LocalDate.of(2026, 12, 31),
        LocalDate.of(2027, 1, 1));

    assertThat(plan.occurrences()).hasSize(1);
    assertThat(plan.occurrences().get(0).businessDate()).isEqualTo(LocalDate.of(2027, 1, 1));
  }

  @Test
  void shouldRejectAmbiguousFiveFieldDayAndWeekCombination() {
    assertThatThrownBy(() -> planner.plan(
        "0 2 1 * MON",
        "Asia/Shanghai",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("语义不等价");
  }

  @Test
  void shouldRejectRangesThatProduceTooManyOccurrences() {
    assertThatThrownBy(() -> planner.plan(
        "* * * * * ?",
        "UTC",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("最多生成");
  }
}
