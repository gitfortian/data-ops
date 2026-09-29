package io.yak.ops.common.bean.dto.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 保存工作流草稿 DTO。 */
public record WorkflowDefinitionUpdateDTO(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    List<@Valid NodeDTO> nodes,
    List<@Valid EdgeDTO> edges,
    Map<String, Object> input,
    Map<String, Object> editorMeta,
    @Min(0) Long workflowTimeoutSeconds,
    @Pattern(
        regexp = "FAIL_FAST|CONTINUE_INDEPENDENT_BRANCHES|TERMINATE_ALL",
        message = "unsupported workflow failureStrategy")
    String failureStrategy) {

  public WorkflowDefinitionUpdateDTO {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
    input = input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input));
    editorMeta = editorMeta == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(editorMeta));
    workflowTimeoutSeconds = workflowTimeoutSeconds == null ? 0L : workflowTimeoutSeconds;
    failureStrategy = failureStrategy == null || failureStrategy.isBlank()
        ? "CONTINUE_INDEPENDENT_BRANCHES"
        : failureStrategy;
  }

  /** 兼容旧调用；新 API 请求应显式传 editorMeta。 */
  public WorkflowDefinitionUpdateDTO(
      String name,
      String description,
      List<NodeDTO> nodes,
      List<EdgeDTO> edges,
      Map<String, Object> input,
      Long workflowTimeoutSeconds,
      String failureStrategy) {
    this(name, description, nodes, edges, input, Map.of(), workflowTimeoutSeconds, failureStrategy);
  }

  public record NodeDTO(
      @NotBlank String id,
      @NotBlank String taskId,
      Long taskAssetId,
      Long taskRevisionId,
      @Min(1) Integer taskRevisionNo,
      Double positionX,
      Double positionY,
      @Min(1) Integer maxAttempts,
      @Min(0) Long retryDelaySeconds,
      @Min(0) Long dispatchTimeoutSeconds,
      @Min(0) Long executionTimeoutSeconds,
      Map<String, String> inputMapping,
      @Pattern(
          regexp = "ALL_SUCCESS|ALL_DONE|NONE_FAILED|ONE_SUCCESS|ALWAYS",
          message = "unsupported triggerRule")
      String triggerRule,
      @Pattern(
          regexp = "FAIL_WORKFLOW|BLOCK_BRANCH|IGNORE_FAILURE",
          message = "unsupported failurePolicy")
      String failurePolicy) {

    public NodeDTO {
      boolean hasCatalogBinding = taskAssetId != null || taskRevisionId != null || taskRevisionNo != null;
      if (hasCatalogBinding) {
        if (taskAssetId == null || taskRevisionId == null || taskRevisionNo == null) {
          throw new IllegalArgumentException("任务资产绑定必须同时包含 taskAssetId/taskRevisionId/taskRevisionNo");
        }
        if (taskAssetId <= 0L || taskRevisionId <= 0L || taskRevisionNo <= 0) {
          throw new IllegalArgumentException("任务资产绑定 ID/版本号必须大于 0");
        }
        taskId = "task-asset:" + taskAssetId;
      }
      positionX = positionX == null ? 0D : positionX;
      positionY = positionY == null ? 0D : positionY;
      maxAttempts = maxAttempts == null ? 1 : maxAttempts;
      retryDelaySeconds = retryDelaySeconds == null ? 0L : retryDelaySeconds;
      dispatchTimeoutSeconds = dispatchTimeoutSeconds == null ? 0L : dispatchTimeoutSeconds;
      executionTimeoutSeconds = executionTimeoutSeconds == null ? 0L : executionTimeoutSeconds;
      inputMapping = inputMapping == null
          ? Map.of()
          : Map.copyOf(new LinkedHashMap<>(inputMapping));
      triggerRule = triggerRule == null || triggerRule.isBlank() ? "ALL_SUCCESS" : triggerRule;
      failurePolicy = failurePolicy == null || failurePolicy.isBlank()
          ? "FAIL_WORKFLOW"
          : failurePolicy;
    }

    /** Backward-compatible constructor for existing legacy task callers/tests. */
    public NodeDTO(
        String id,
        String taskId,
        Double positionX,
        Double positionY,
        Integer maxAttempts,
        Long retryDelaySeconds,
        Long dispatchTimeoutSeconds,
        Long executionTimeoutSeconds,
        Map<String, String> inputMapping,
        String triggerRule,
        String failurePolicy) {
      this(
          id,
          taskId,
          null,
          null,
          null,
          positionX,
          positionY,
          maxAttempts,
          retryDelaySeconds,
          dispatchTimeoutSeconds,
          executionTimeoutSeconds,
          inputMapping,
          triggerRule,
          failurePolicy);
    }
  }

  public record EdgeDTO(@NotBlank String source, @NotBlank String target) {
  }
}
