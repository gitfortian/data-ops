package io.yak.ops.common.bean.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 更新工作流调度定义。 */
public record WorkflowScheduleUpdateDTO(
    @NotBlank @Size(max = 100) String name,
    @NotBlank String cronExpression,
    String timezone,
    Instant startTime,
    Instant endTime,
    String executionStrategy,
    String misfireStrategy,
    Map<String, Object> input) {

  public WorkflowScheduleUpdateDTO {
    timezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
    executionStrategy = executionStrategy == null || executionStrategy.isBlank()
        ? "SERIAL_WAIT"
        : executionStrategy.trim().toUpperCase();
    misfireStrategy = misfireStrategy == null || misfireStrategy.isBlank()
        ? "FIRE_ONCE"
        : misfireStrategy.trim().toUpperCase();
    input = input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input));
  }
}
