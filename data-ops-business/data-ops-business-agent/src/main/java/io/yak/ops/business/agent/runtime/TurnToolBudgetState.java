package io.yak.ops.business.agent.runtime;

import io.agentscope.core.state.State;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.domain.ToolBudgetSnapshot;

/** Only the current turn's auxiliary execution state occupies this StateStore slot. */
record TurnToolBudgetState(String turnId, GovernanceTarget target, ToolBudgetSnapshot budget)
    implements State {
  static final String KEY = "yak_tool_budget";

  static void attach(AgentStateStore store, RuntimeContext context, String turnId, boolean resumed,
      AgentProperties.Execution config) {
    var execution = context.get(AgentExecutionContext.class);
    String identity = turnId == null ? java.util.UUID.randomUUID().toString() : turnId;
    try {
      var previous = store.get(context.getUserId(), context.getSessionId(), KEY, TurnToolBudgetState.class).orElse(null);
      boolean sameTurn = resumed && previous != null && identity.equals(previous.turnId());
      if (sameTurn && !java.util.Objects.equals(execution.target(), previous.target())) {
        throw new IllegalArgumentException("[TASK_TARGET_MISMATCH] 恢复任务目标与原轮次不一致");
      }
      var budget = sameTurn ? previous.budget()
          : new ToolBudgetSnapshot(config.getMaxToolCalls(), config.getMaxFailuresPerTool(), 0, java.util.Map.of());
      store.save(context.getUserId(), context.getSessionId(), KEY,
          new TurnToolBudgetState(identity, execution.target(), budget));
      context.put("yak.toolBudget.initializedForResume", resumed && !sameTurn);
      execution.configureBudget(budget, next -> store.save(context.getUserId(), context.getSessionId(), KEY,
          new TurnToolBudgetState(identity, execution.target(), next)));
    } catch (IllegalArgumentException invalid) {
      throw invalid;
    } catch (RuntimeException unavailable) {
      throw new IllegalStateException("[TOOL_BUDGET_UNAVAILABLE] 无法读取或保存执行预算，未开始推理");
    }
  }
}
