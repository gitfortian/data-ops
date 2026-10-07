package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.MetricExplanationContext;
import io.yak.ops.business.agent.domain.MetricExplanationProposal;
import io.yak.ops.business.agent.domain.MetricExplanationSuggestion;
import io.yak.ops.business.agent.domain.MetricExplanationTarget;
import io.yak.ops.business.metric.api.MetricExplanationQueryApi;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class MetricExplanationGateway {
  private final ObjectProvider<MetricExplanationQueryApi> metrics;
  public MetricExplanationContext prepare(MetricExplanationTarget target) {
    var api = metrics.getIfAvailable();
    if (api == null) throw new IllegalStateException("指标口径读取未装配");
    var source = api.require(target.metricId(), target.version());
    return new MetricExplanationContext(source.versionId(), source.definition(), source.facts().stream()
        .map(f -> new MetricExplanationContext.Fact(f.key(), f.label(), f.value())).toList());
  }
  public MetricExplanationSuggestion validate(MetricExplanationTarget target, MetricExplanationContext original,
      MetricExplanationProposal proposal, int skillVersion, String skillHash) {
    var current = prepare(target);
    if (original.versionId() != current.versionId() || !original.definition().equals(current.definition())) {
      throw new IllegalArgumentException("指标快照已变化，请重新生成");
    }
    if (proposal == null || proposal.candidates() == null || proposal.candidates().size() > 1
        || proposal.questions() == null || proposal.questions().size() > 3 || proposal.questions().stream().anyMatch(q -> !text(q))) invalid();
    var candidates = proposal.candidates().stream().map(c -> {
      if (c == null || !text(c.businessDescription()) || c.statements() == null || c.statements().isEmpty() || c.statements().size() > 5) invalid();
      var statements = c.statements().stream().map(s -> {
        if (s == null || !text(s.text()) || s.factKeys() == null || s.factKeys().isEmpty() || s.factKeys().size() > 4
            || new HashSet<>(s.factKeys()).size() != s.factKeys().size()) invalid();
        var evidence = s.factKeys().stream().map(key -> {
          if (key == null || original.facts().stream().noneMatch(f -> f.key().equals(key))) invalid();
          return current.facts().stream().filter(f -> f.key().equals(key)).findFirst()
              .orElseThrow(() -> new IllegalArgumentException("口径事实已变化"));
        }).toList();
        return new MetricExplanationSuggestion.Statement(s.text(), evidence);
      }).toList();
      return new MetricExplanationSuggestion.Candidate(c.businessDescription(), statements);
    }).toList();
    return new MetricExplanationSuggestion("METRIC_EXPLANATION", target, original.definition(), skillVersion, skillHash, false, candidates, proposal.questions());
  }
  public MetricExplanationSuggestion revalidate(MetricExplanationSuggestion value) {
    if (value == null || !"METRIC_EXPLANATION".equals(value.kind()) || value.target() == null
        || value.candidates() == null || value.candidates().size() != 1) invalid();
    var source = prepare(value.target());
    if (!source.definition().equals(value.expectedDefinition())) throw new IllegalArgumentException("指标版本已变化，请重新生成");
    // Treat HTTP artifacts as untrusted; never use client-provided evidence labels or values.
    var proposal = new MetricExplanationProposal(value.candidates().stream().map(c -> {
      if (c == null || c.statements() == null || c.statements().size() > 5) invalid();
      return new MetricExplanationProposal.Explanation(c.businessDescription(), c.statements().stream().map(s -> {
        if (s == null || s.evidence() == null || s.evidence().size() > 4 || s.evidence().stream().anyMatch(java.util.Objects::isNull)) invalid();
        return new MetricExplanationProposal.Statement(s.text(), s.evidence().stream().map(MetricExplanationContext.Fact::key).toList());
      }).toList());
    }).toList(), List.of());
    return validate(value.target(), source, proposal, value.skillVersion(), value.skillHash());
  }
  private static boolean text(String text) { return text != null && !text.isBlank() && text.length() <= 512; }
  private static void invalid() { throw new IllegalArgumentException("指标解释输出或事实引用不符合协议"); }
}
