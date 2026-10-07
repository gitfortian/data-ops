package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricExplanationContext(long versionId, String definition, List<Fact> facts) {
  public record Fact(String key, String label, String value) {}
}
