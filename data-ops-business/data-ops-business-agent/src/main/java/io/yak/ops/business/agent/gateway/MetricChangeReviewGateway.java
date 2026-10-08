package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.MetricChangeReviewContext;
import io.yak.ops.business.agent.domain.MetricChangeReviewProposal;
import io.yak.ops.business.agent.domain.MetricChangeReviewSuggestion;
import io.yak.ops.business.agent.domain.MetricChangeReviewTarget;
import io.yak.ops.business.metric.api.MetricChangeReviewQueryApi;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class MetricChangeReviewGateway {
  private final ObjectProvider<MetricChangeReviewQueryApi> metrics;
  public MetricChangeReviewContext prepare(MetricChangeReviewTarget target) {
    var api = metrics.getIfAvailable();
    if (api == null) throw new IllegalStateException("版本变更核对未装配");
    var value = api.prepare(target.metricId(), target.version());
    if (!"READY".equals(value.status()) || value.metricId() != target.metricId() || value.version() != target.version()
        || !Objects.equals(value.publishedVersion(), target.publishedVersion())
        || !Objects.equals(value.publicationEventId(), target.publicationEventId())
        || !Objects.equals(value.definition(), target.definition())) throw new IllegalArgumentException("版本、发布或证据已变化，请重新准备");
    return new MetricChangeReviewContext(value.definition(), value.preparedAt(), value.differences().stream()
        .map(d -> new MetricChangeReviewContext.Difference(d.key(), d.label(), d.before(), d.after())).toList(),
        value.facts().stream().map(f -> new MetricChangeReviewContext.Fact(f.key(), f.label(), f.value())).toList(),
        value.coverage().stream().map(c -> new MetricChangeReviewContext.Coverage(c.key(), c.label(), c.status(), c.description())).toList());
  }
  public MetricChangeReviewSuggestion validate(MetricChangeReviewTarget target, MetricChangeReviewContext original,
      MetricChangeReviewProposal value, int skillVersion, String skillHash) {
    var current = prepare(target);
    if (!current.definition().equals(original.definition())) invalid();
    if (value == null || value.candidates() == null || value.candidates().size() > 1 || value.questions() == null
        || value.questions().size() > 3 || value.questions().stream().anyMatch(q -> !text(q))) invalid();
    var candidates = value.candidates().stream().map(c -> {
      if (c == null || c.statements() == null || c.statements().isEmpty() || c.statements().size() > 5
          || c.checks() == null || c.checks().size() > 5) invalid();
      return new MetricChangeReviewSuggestion.Review(statements(c.statements(), current), statements(c.checks(), current));
    }).toList();
    var result = new MetricChangeReviewSuggestion("METRIC_CHANGE_REVIEW", target, current.definition(), skillVersion, skillHash, false,
        current, candidates, value.questions());
    try {
      if (new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result).length() > 60000) invalid();
    } catch (com.fasterxml.jackson.core.JsonProcessingException failed) { invalid(); }
    return result;
  }
  private static List<MetricChangeReviewSuggestion.Statement> statements(List<MetricChangeReviewProposal.Statement> values,
      MetricChangeReviewContext source) {
    return values.stream().map(s -> {
      if (s == null || !text(s.text()) || s.factKeys() == null || s.factKeys().isEmpty() || s.factKeys().size() > 4
          || new HashSet<>(s.factKeys()).size() != s.factKeys().size()) invalid();
      var evidence = s.factKeys().stream().map(key -> source.facts().stream().filter(f -> f.key().equals(key)).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("变更说明引用不在本轮事实中"))).toList();
      return new MetricChangeReviewSuggestion.Statement(s.text(), evidence);
    }).toList();
  }
  private static boolean text(String value) { return value != null && !value.isBlank() && value.length() <= 512; }
  private static void invalid() { throw new IllegalArgumentException("版本变更说明不符合协议"); }
}
