package io.yak.ops.common.bean.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

/** 创建工作流历史补数批次。 */
public record WorkflowBackfillCreateDTO(
    @NotBlank String scheduleId,
    String name,
    @NotNull LocalDate startBusinessDate,
    @NotNull LocalDate endBusinessDate,
    String executionStrategy,
    Map<String, Object> input) {

  public WorkflowBackfillCreateDTO {
    input = input == null ? Map.of() : Map.copyOf(input);
  }
}
