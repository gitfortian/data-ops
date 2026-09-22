package io.yak.ops.common.bean.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建工作流定义 DTO。 */
public record WorkflowDefinitionCreateDTO(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description) {
}
