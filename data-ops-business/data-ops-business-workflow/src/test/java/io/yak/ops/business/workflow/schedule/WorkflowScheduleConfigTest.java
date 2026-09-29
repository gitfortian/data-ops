package io.yak.ops.business.workflow.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowScheduleConfigTest {
  private final WorkflowScheduleValidator validator = new WorkflowScheduleValidator();

  @Test
  void shouldApplySafeDefaults() {
    var value = validator.normalize(
        "每日同步", "0 0 2 * * ?", null, null, null, null, null, Map.of());
    assertThat(value.timezone()).isEqualTo("Asia/Shanghai");
    assertThat(value.executionStrategy()).isEqualTo("SERIAL_WAIT");
    assertThat(value.misfireStrategy()).isEqualTo("FIRE_ONCE");
  }

  @Test
  void shouldRejectInvalidCronShape() {
    assertThatThrownBy(() -> validator.normalize(
        "每日同步", "* * *", "Asia/Shanghai", null, null, null, null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
