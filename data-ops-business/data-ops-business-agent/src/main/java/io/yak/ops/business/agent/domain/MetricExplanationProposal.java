package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricExplanationProposal(List<Explanation> candidates, List<String> questions) {
  public record Explanation(String businessDescription, List<Statement> statements) {}
  public record Statement(String text, List<String> factKeys) {}
}
