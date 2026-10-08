package io.yak.ops.business.agent.toolset;

import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.MetricDraftContext;
import io.yak.ops.business.agent.domain.MetricDraftProposal;
import io.yak.ops.business.agent.domain.MetricDraftSuggestion;
import io.yak.ops.business.agent.domain.MetricDraftTarget;
import io.yak.ops.business.agent.gateway.MetricDraftGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class MetricDraftTools implements AgentToolBox {
  private final AgentToolExecution execution;
  private final MetricDraftGateway gateway;
  @io.agentscope.core.tool.Tool(name = "get_metric_draft_context", description = "只读本轮绑定的指标草稿依赖与模型字段，不切换目标、不执行或保存。")
  public MetricDraftContext context(RuntimeContext context) {
    var target = AgentToolExecution.state(context).target();
    if (target == null || target.metricDraft() == null) throw new IllegalArgumentException("缺少指标草稿目标");
    return prepare(context, target.metricDraft());
  }
  public MetricDraftContext prepare(RuntimeContext context, MetricDraftTarget target) {
    return execution.call(context, "get_metric_draft_context", () -> gateway.prepare(target));
  }
  public MetricDraftSuggestion validate(RuntimeContext context, MetricDraftTarget target, MetricDraftContext source,
      MetricDraftProposal value, int version, String hash) {
    return execution.call(context, "get_metric_draft_context", () -> gateway.validate(target, source, value, version, hash));
  }
  public MetricDraftSuggestion revalidate(MetricDraftSuggestion value) { return gateway.revalidate(value); }
}
