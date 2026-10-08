package io.yak.ops.business.agent.domain;

import java.util.List;

/** Model supplied keys are resolved exclusively against the prepared source. */
public record MetricChangeReviewProposal(List<Review> candidates, List<String> questions) {
  public record Review(List<Statement> statements, List<Statement> checks) {}
  public record Statement(String text, List<String> factKeys) {}
}
