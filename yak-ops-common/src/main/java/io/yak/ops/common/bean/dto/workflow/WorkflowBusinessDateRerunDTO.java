package io.yak.ops.common.bean.dto.workflow;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

/** 从历史实例的调度血缘按指定 businessDate 创建运维补跑。 */
public record WorkflowBusinessDateRerunDTO(
    @NotNull LocalDate businessDate,
    String executionStrategy,
    Map<String, Object> input) {

  public WorkflowBusinessDateRerunDTO {
    input = input == null ? Map.of() : Map.copyOf(input);
  }
}
