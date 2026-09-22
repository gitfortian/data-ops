package io.yak.ops.business.workflow.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作流一次运行的不可变领域规格。 */
public record WorkflowRunSpec(
    String name,
    List<WorkflowNodeSpec> nodes,
    List<WorkflowEdgeSpec> edges,
    Map<String, Object> input,
    long workflowTimeoutSeconds,
    String failureStrategy) {

  public WorkflowRunSpec {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
    input = input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input));
    failureStrategy = failureStrategy == null || failureStrategy.isBlank()
        ? "CONTINUE_INDEPENDENT_BRANCHES"
        : failureStrategy;
  }

  /**
   * 不修改发布版本本身；仅在统一 Launch 调用栈内把 Schedule/Backfill 参数覆盖到运行 input。
   * Runtime 最终仍通过 engine.start(definitionId, request.input()) 获取一次不可变输入快照。
   */
  @Override
  public Map<String, Object> input() {
    Map<String, Object> overrides = WorkflowRunInputScope.current();
    if (overrides.isEmpty()) return input;
    Map<String, Object> merged = new LinkedHashMap<>(input);
    merged.putAll(overrides);
    return Map.copyOf(merged);
  }
}
