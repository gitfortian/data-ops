package io.yak.ops.business.metric.api;

import java.util.List;

/** Authorized preparation and deterministic draft checks. Neither method writes a Metric. */
public interface MetricDraftQueryApi {
  Context prepare(Input input);
  Draft validate(Input input, String expectedDefinition, Draft draft);
  record Input(Long metricId, Integer version, String metricType, Long modelId, List<Long> upstreamIds, String requirement) {
    public Input {
      if ((metricId == null) != (version == null) || (metricId != null && (metricId <= 0 || version <= 0))
          || !List.of("ATOMIC", "DERIVED", "COMPOSITE").contains(metricType == null ? "" : metricType)
          || requirement == null || requirement.isBlank() || requirement.length() > 512
          || upstreamIds == null || upstreamIds.size() > 5 || upstreamIds.stream().anyMatch(id -> id == null || id <= 0)
          || upstreamIds.stream().distinct().count() != upstreamIds.size()
          || ("ATOMIC".equals(metricType) && (modelId == null || modelId <= 0 || !upstreamIds.isEmpty()))
          || (!"ATOMIC".equals(metricType) && (modelId != null || upstreamIds.isEmpty()))
          || ("DERIVED".equals(metricType) && upstreamIds.size() != 1)) throw new IllegalArgumentException("指标草稿的类型、依赖或需求无效");
      upstreamIds = List.copyOf(upstreamIds);
    }
  }
  record Field(String name, String type, String description) {}
  record Upstream(long id, int version, String code, String name, String type, String measure, String filter) {}
  record Context(String definition, List<Field> fields, List<Upstream> upstream) {}
  record Qualifier(String field, String op, String value) {}
  record Token(String operator, Long metricId) {}
  record Draft(String name, String description, String period, String aggregation, String field,
      List<Qualifier> qualifiers, List<Token> tokens) {}
}
