package io.yak.ops.business.agent.domain;

import java.util.List;

/** Untrusted SDK output; source-owned validation determines whether it can be delivered. */
public record MetricDraftProposal(List<Draft> candidates, List<String> questions) {
  public record Qualifier(String field, String op, String value) {}
  public record Token(String operator, Long metricId) {}
  public record Draft(String name, String description, String period, String aggregation, String field,
      List<Qualifier> qualifiers, List<Token> tokens) {}
}
