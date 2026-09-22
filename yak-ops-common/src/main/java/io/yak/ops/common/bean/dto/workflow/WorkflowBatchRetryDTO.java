package io.yak.ops.common.bean.dto.workflow;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 批量恢复失败工作流实例。 */
public record WorkflowBatchRetryDTO(
    @NotEmpty @Size(max = 100) List<String> executionIds) {

  public WorkflowBatchRetryDTO {
    executionIds = executionIds == null ? List.of() : List.copyOf(executionIds);
  }
}
