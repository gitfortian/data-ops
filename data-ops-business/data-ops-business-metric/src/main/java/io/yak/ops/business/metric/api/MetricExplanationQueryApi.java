package io.yak.ops.business.metric.api;

import java.util.List;

/** Authorized, bounded immutable-version projection. No calculation or publication claims. */
public interface MetricExplanationQueryApi {
  Context require(long metricId, int version);
  default Context requireSnapshot(long metricId, int version) { throw new IllegalStateException("历史版本解释未装配"); }
  record Context(long versionId, long metricId, int version, String definition, List<Fact> facts) {}
  record Fact(String key, String label, String value) {}
}
