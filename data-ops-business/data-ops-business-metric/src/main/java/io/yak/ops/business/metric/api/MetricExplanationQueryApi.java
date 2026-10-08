package io.yak.ops.business.metric.api;

import java.util.List;

/** Authorized, bounded immutable-version projection. No calculation or publication claims. */
public interface MetricExplanationQueryApi {
  Context require(long metricId, int version);
  record Context(long versionId, long metricId, int version, String definition, List<Fact> facts) {}
  record Fact(String key, String label, String value) {}
}
