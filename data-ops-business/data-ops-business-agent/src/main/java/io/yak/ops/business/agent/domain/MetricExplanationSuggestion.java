package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricExplanationSuggestion(String kind, MetricExplanationTarget target, String expectedDefinition,
    int skillVersion, String skillHash, boolean truncated, List<Candidate> candidates, List<String> questions) {
  public record Candidate(String businessDescription, List<Statement> statements) {}
  public record Statement(String text, List<MetricExplanationContext.Fact> evidence) {}
}
