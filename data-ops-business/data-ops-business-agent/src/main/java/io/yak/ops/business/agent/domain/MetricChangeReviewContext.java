package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricChangeReviewContext(String definition, String preparedAt,
    List<Difference> differences, List<Fact> facts, List<Coverage> coverage) {
  public record Fact(String key, String label, String value) {}
  public record Difference(String key, String label, String before, String after) {}
  public record Coverage(String key, String label, String status, String description) {}
}
