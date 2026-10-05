package io.yak.ops.business.agent.domain;

import java.util.Map;

/** Conservative pre-execution reservations survive HITL; no arguments, credentials or business facts. */
public record ToolBudgetSnapshot(int maxCalls, int maxFailuresPerTool, int usedCalls,
    Map<String, Integer> failures) {
  public ToolBudgetSnapshot {
    if (maxCalls < 1 || maxFailuresPerTool < 1 || usedCalls < 0 || usedCalls > maxCalls) {
      throw new IllegalArgumentException("工具预算配置或状态无效");
    }
    failures = Map.copyOf(failures);
  }
}
