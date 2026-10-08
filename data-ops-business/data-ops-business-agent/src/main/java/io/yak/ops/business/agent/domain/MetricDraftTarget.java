package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricDraftTarget(Long metricId, Integer version, String metricType, Long modelId,
    List<Long> upstreamIds, String requirement) {
  public MetricDraftTarget {
    if ((metricId == null) != (version == null) || (metricId != null && (metricId <= 0 || version <= 0))
        || !List.of("ATOMIC", "DERIVED", "COMPOSITE").contains(metricType == null ? "" : metricType)
        || requirement == null || requirement.isBlank() || requirement.length() > 512
        || upstreamIds == null || upstreamIds.size() > 5 || upstreamIds.stream().anyMatch(id -> id == null || id <= 0)
        || upstreamIds.stream().distinct().count() != upstreamIds.size()
        || ("ATOMIC".equals(metricType) && (modelId == null || modelId <= 0 || !upstreamIds.isEmpty()))
        || (!"ATOMIC".equals(metricType) && (modelId != null || upstreamIds.isEmpty()))
        || ("DERIVED".equals(metricType) && upstreamIds.size() != 1)) throw new IllegalArgumentException("指标草稿目标无效");
    upstreamIds = List.copyOf(upstreamIds);
  }
}
