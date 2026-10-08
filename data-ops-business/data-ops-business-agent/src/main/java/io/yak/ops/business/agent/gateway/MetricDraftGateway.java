package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.MetricDraftContext;
import io.yak.ops.business.agent.domain.MetricDraftProposal;
import io.yak.ops.business.agent.domain.MetricDraftSuggestion;
import io.yak.ops.business.agent.domain.MetricDraftTarget;
import io.yak.ops.business.metric.api.MetricDraftQueryApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class MetricDraftGateway {
  private final ObjectProvider<MetricDraftQueryApi> metrics;
  public MetricDraftContext prepare(MetricDraftTarget target) {
    var context = api().prepare(input(target));
    return new MetricDraftContext(context.definition(), context.fields().stream().map(f -> new MetricDraftContext.Field(f.name(), f.type(), f.description())).toList(),
        context.upstream().stream().map(u -> new MetricDraftContext.Upstream(u.id(), u.version(), u.code(), u.name(), u.type(), u.measure(), u.filter())).toList());
  }
  public MetricDraftSuggestion validate(MetricDraftTarget target, MetricDraftContext original,
      MetricDraftProposal proposal, int skillVersion, String skillHash) {
    var source = prepare(target);
    if (!source.definition().equals(original.definition())) throw new IllegalArgumentException("指标依赖或模型结构已变化");
    if (proposal == null || proposal.candidates() == null || proposal.candidates().size() > 1
        || proposal.questions() == null || proposal.questions().size() > 3
        || proposal.questions().stream().anyMatch(q -> q == null || q.isBlank() || q.length() > 512)) invalid();
    for (var draft : proposal.candidates()) {
      if (draft == null || draft.qualifiers() == null || draft.tokens() == null || draft.qualifiers().size() > 5 || draft.tokens().size() > 31
          || draft.qualifiers().stream().anyMatch(java.util.Objects::isNull) || draft.tokens().stream().anyMatch(java.util.Objects::isNull)) invalid();
      api().validate(input(target), source.definition(), new MetricDraftQueryApi.Draft(draft.name(), draft.description(), draft.period(), draft.aggregation(), draft.field(),
          draft.qualifiers().stream().map(q -> new MetricDraftQueryApi.Qualifier(q.field(), q.op(), q.value())).toList(),
          draft.tokens().stream().map(t -> new MetricDraftQueryApi.Token(t.operator(), t.metricId())).toList()));
    }
    return new MetricDraftSuggestion("METRIC_DRAFT", target, original.definition(), skillVersion, skillHash, false,
        proposal.candidates(), proposal.questions(), source);
  }
  public MetricDraftSuggestion revalidate(MetricDraftSuggestion value) {
    if (value == null || !"METRIC_DRAFT".equals(value.kind()) || value.target() == null || value.candidates() == null || value.candidates().size() != 1) invalid();
    var source = prepare(value.target());
    if (!source.definition().equals(value.expectedDefinition())) throw new IllegalArgumentException("指标草稿已失效，请重新生成");
    return validate(value.target(), source, new MetricDraftProposal(value.candidates(), List.of()), value.skillVersion(), value.skillHash());
  }
  private MetricDraftQueryApi api() {
    var api = metrics.getIfAvailable(); if (api == null) throw new IllegalStateException("指标草稿读取未装配"); return api;
  }
  private static MetricDraftQueryApi.Input input(MetricDraftTarget t) {
    if (t == null) throw new IllegalArgumentException("缺少指标草稿目标");
    return new MetricDraftQueryApi.Input(t.metricId(), t.version(), t.metricType(), t.modelId(), t.upstreamIds(), t.requirement());
  }
  private static void invalid() { throw new IllegalArgumentException("指标草稿输出不符合协议"); }
}
