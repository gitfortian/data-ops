package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricDraftContext(String definition, List<Field> fields, List<Upstream> upstream) {
  public record Field(String name, String type, String description) {}
  public record Upstream(long id, int version, String code, String name, String type, String measure, String filter) {}
}
