package io.yak.ops.business.agent.toolset;

import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.StandardMatchContext;
import io.yak.ops.business.agent.domain.StandardMatchProposal;
import io.yak.ops.business.agent.domain.StandardMatchSuggestion;
import io.yak.ops.business.agent.domain.StandardMatchTarget;
import io.yak.ops.business.agent.gateway.StandardMatchGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Deterministic reads/validation under the existing authenticated execution seam, not model commands. */
@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class StandardMatchTools implements AgentToolBox {
  private final AgentToolExecution execution;
  private final StandardMatchGateway gateway;
  @io.agentscope.core.tool.Tool(name = "get_standard_match_context", description = "只读当前绑定字段的授权模型定义和最多20项启用类型标准；不能切换对象。")
  public StandardMatchContext context(RuntimeContext context) {
    var state = AgentToolExecution.state(context);
    if (state.target() == null || state.target().standardMatch() == null) {
      throw new IllegalArgumentException("缺少标准匹配目标");
    }
    return prepare(context, state.target().standardMatch());
  }
  public StandardMatchContext prepare(RuntimeContext context, StandardMatchTarget target) {
    return execution.call(context, "get_standard_match_context", () -> gateway.prepare(target));
  }
  public StandardMatchSuggestion validate(RuntimeContext context, StandardMatchTarget target, StandardMatchContext source,
      StandardMatchProposal value, int skillVersion, String skillHash) {
    return execution.call(context, "get_standard_match_context", () -> gateway.validate(target, source, value, skillVersion, skillHash));
  }
  public StandardMatchSuggestion revalidate(StandardMatchSuggestion value) { return gateway.revalidate(value); }
}
