package io.yak.ops.business.agent.toolset;

import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.MetricExplanationContext;
import io.yak.ops.business.agent.domain.MetricExplanationProposal;
import io.yak.ops.business.agent.domain.MetricExplanationSuggestion;
import io.yak.ops.business.agent.domain.MetricExplanationTarget;
import io.yak.ops.business.agent.gateway.MetricExplanationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class MetricExplanationTools implements AgentToolBox {
  private final AgentToolExecution execution;
  private final MetricExplanationGateway gateway;
  @io.agentscope.core.tool.Tool(name = "get_metric_caliber_context", description = "只读绑定指标当前已保存版本的口径事实；不可切换指标、执行SQL或断言验证发布状态。")
  public MetricExplanationContext context(RuntimeContext context) {
    var target = AgentToolExecution.state(context).target();
    if (target == null || target.metricExplanation() == null) throw new IllegalArgumentException("缺少指标版本目标");
    return prepare(context, target.metricExplanation());
  }
  public MetricExplanationContext prepare(RuntimeContext context, MetricExplanationTarget target) {
    return execution.call(context, "get_metric_caliber_context", () -> gateway.prepare(target));
  }
  public MetricExplanationSuggestion validate(RuntimeContext context, MetricExplanationTarget target, MetricExplanationContext source,
      MetricExplanationProposal value, int skillVersion, String skillHash) {
    return execution.call(context, "get_metric_caliber_context", () -> gateway.validate(target, source, value, skillVersion, skillHash));
  }
  public MetricExplanationSuggestion revalidate(MetricExplanationSuggestion value) { return gateway.revalidate(value); }
}
