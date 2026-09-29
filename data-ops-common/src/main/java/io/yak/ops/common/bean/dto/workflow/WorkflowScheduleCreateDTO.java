package io.yak.ops.common.bean.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 创建工作流调度定义。 */
public record WorkflowScheduleCreateDTO(
    @NotBlank String workflowId,
    @NotBlank @Size(max = 100) String name,
    @NotBlank String cronExpression,
    String timezone,
    Instant startTime,
    Instant endTime,
    @Pattern(
        regexp = "PARALLEL|SERIAL_WAIT|SERIAL_DISCARD",
        message = "unsupported workflow schedule executionStrategy")
    String executionStrategy,
    @Pattern(
        regexp = "SKIP|FIRE_ONCE",
        message = "unsupported workflow schedule misfireStrategy")
    String misfireStrategy,
    Map<String, Object> input) {

  public WorkflowScheduleCreateDTO {
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
