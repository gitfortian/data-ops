package io.yak.ops.business.agent.toolset;

import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.MetricChangeReviewContext;
import io.yak.ops.business.agent.domain.MetricChangeReviewProposal;
import io.yak.ops.business.agent.domain.MetricChangeReviewSuggestion;
import io.yak.ops.business.agent.domain.MetricChangeReviewTarget;
import io.yak.ops.business.agent.gateway.MetricChangeReviewGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class MetricChangeReviewTools implements AgentToolBox {
  private final AgentToolExecution execution;
  private final MetricChangeReviewGateway gateway;
  @io.agentscope.core.tool.Tool(name = "get_metric_change_review_context", description = "只读固定草稿与发布版本差异、精确验证和已知声明引用；不验证、发布或查询数据。")
  public MetricChangeReviewContext context(RuntimeContext context) {
    var target = AgentToolExecution.state(context).target();
    if (target == null || target.metricChangeReview() == null) throw new IllegalArgumentException("缺少固定版本对");
    return prepare(context, target.metricChangeReview());
  }
  public MetricChangeReviewContext prepare(RuntimeContext context, MetricChangeReviewTarget target) {
    return execution.call(context, "get_metric_change_review_context", () -> gateway.prepare(target));
  }
  public MetricChangeReviewSuggestion validate(RuntimeContext context, MetricChangeReviewTarget target,
      MetricChangeReviewContext original, MetricChangeReviewProposal value, int version, String hash) {
    return execution.call(context, "get_metric_change_review_context", () -> gateway.validate(target, original, value, version, hash));
  }
}
