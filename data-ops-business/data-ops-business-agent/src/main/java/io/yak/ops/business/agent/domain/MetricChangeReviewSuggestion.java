package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricChangeReviewSuggestion(String kind, MetricChangeReviewTarget target, String expectedDefinition,
    int skillVersion, String skillHash, boolean truncated, MetricChangeReviewContext source,
    List<Review> candidates, List<String> questions) {
  public record Review(List<Statement> statements, List<Statement> checks) {}
  public record Statement(String text, List<MetricChangeReviewContext.Fact> evidence) {}
}
