package io.yak.ops.business.workflow.definition;

import io.yak.ops.business.workflow.domain.WorkflowEdgeSpec;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles editor-only Start connections into the executable task DAG. */
final class WorkflowStartGraphCompiler {

  private static final String START_META_KEY = "__yak_start__";
  private static final String NEXT_NODE_IDS_KEY = "nextNodeIds";

  private WorkflowStartGraphCompiler() {}

  /** 兼容旧调用：显式 Start 信息曾存放在 input。 */
  static RuntimeGraph compile(
      List<WorkflowNodeSpec> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> input) {
    return compile(nodes, edges, Map.of(), input);
  }

  /**
   * 新模型优先从 editorMeta 读取 Start 连线；legacyInput 仅用于兼容历史定义。
   * Runtime input 不参与新的画布拓扑建模。
   */
  static RuntimeGraph compile(
      List<WorkflowNodeSpec> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> editorMeta,
      Map<String, Object> legacyInput) {
    Map<String, WorkflowNodeSpec> nodesById = new LinkedHashMap<>();
    for (WorkflowNodeSpec node : nodes) nodesById.put(node.id(), node);

    Map<String, List<String>> adjacency = new LinkedHashMap<>();
    Set<String> taskTargets = new LinkedHashSet<>();
    for (String nodeId : nodesById.keySet()) adjacency.put(nodeId, new ArrayList<>());
    for (WorkflowEdgeSpec edge : edges) {
      if (!nodesById.containsKey(edge.source()) || !nodesById.containsKey(edge.target())) continue;
      adjacency.get(edge.source()).add(edge.target());
      taskTargets.add(edge.target());
    }

    StartSelection selection = readStartSelection(editorMeta, legacyInput);
    List<String> startNodeIds;
    if (selection.explicit()) {
      startNodeIds = selection.nodeIds();
      if (startNodeIds.isEmpty()) throw new IllegalStateException("开始节点至少需要连接一个任务节点");
      for (String nodeId : startNodeIds) {
        if (!nodesById.containsKey(nodeId)) {
          throw new IllegalStateException("开始节点连接了不存在的任务节点：" + nodeId);
        }
        if (taskTargets.contains(nodeId)) {
          throw new IllegalStateException("开始节点只能连接没有前置任务的根节点：" + nodeId);
        }
      }
    } else {
      startNodeIds = nodesById.keySet().stream()
          .filter(nodeId -> !taskTargets.contains(nodeId))
          .toList();
    }

    if (startNodeIds.isEmpty()) {
      throw new IllegalStateException("工作流没有可从开始节点进入的任务节点");
    }

    Set<String> reachable = new LinkedHashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>(startNodeIds);
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      if (!reachable.add(current)) continue;
      for (String next : adjacency.getOrDefault(current, List.of())) queue.addLast(next);
    }

    if (selection.explicit()) {
      for (WorkflowEdgeSpec edge : edges) {
        if (reachable.contains(edge.target()) && !reachable.contains(edge.source())) {
          throw new IllegalStateException(
              "开始节点未接入完整前置分支：" + edge.source() + " -> " + edge.target());
        }
      }
    }

    List<WorkflowNodeSpec> runtimeNodes = nodes.stream()
        .filter(node -> reachable.contains(node.id()))
        .toList();
    List<WorkflowEdgeSpec> runtimeEdges = edges.stream()
        .filter(edge -> reachable.contains(edge.source()) && reachable.contains(edge.target()))
        .toList();
    return new RuntimeGraph(runtimeNodes, runtimeEdges);
  }

  private static StartSelection readStartSelection(
      Map<String, Object> editorMeta,
      Map<String, Object> legacyInput) {
    StartSelection current = readStartSelection(editorMeta);
    return current.explicit() ? current : readStartSelection(legacyInput);
  }

  private static StartSelection readStartSelection(Map<String, Object> source) {
    if (source == null) return StartSelection.legacy();
    Object rawMeta = source.get(START_META_KEY);
    if (!(rawMeta instanceof Map<?, ?> meta) || !meta.containsKey(NEXT_NODE_IDS_KEY)) {
      return StartSelection.legacy();
    }

    Object rawNodeIds = meta.get(NEXT_NODE_IDS_KEY);
    if (rawNodeIds == null) return StartSelection.explicit(List.of());
    if (!(rawNodeIds instanceof List<?> values)) {
      throw new IllegalStateException("开始节点连接信息格式不正确");
    }

    LinkedHashSet<String> nodeIds = new LinkedHashSet<>();
    for (Object value : values) {
      if (value instanceof String nodeId && !nodeId.isBlank()) nodeIds.add(nodeId.trim());
    }
    return StartSelection.explicit(List.copyOf(nodeIds));
  }

  record RuntimeGraph(List<WorkflowNodeSpec> nodes, List<WorkflowEdgeSpec> edges) {}

  private record StartSelection(boolean explicit, List<String> nodeIds) {
    static StartSelection legacy() {
      return new StartSelection(false, List.of());
    }

    static StartSelection explicit(List<String> nodeIds) {
      return new StartSelection(true, nodeIds);
    }
  }
}
