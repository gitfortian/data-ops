package io.yak.ops.business.metric.api;

import java.util.List;

/** Authorized bounded read projection; no validation or publication command is invoked. */
public interface MetricChangeReviewQueryApi {
  Context prepare(long metricId, int version);
  record Fact(String key, String label, String value) {}
  record Difference(String key, String label, String before, String after) {}
  record Coverage(String key, String label, String status, String description) {}
  record Context(String status, long metricId, int version, Long publishedVersionId,
      Integer publishedVersion, Long publicationEventId, String definition, String preparedAt,
      List<Difference> differences, List<Fact> facts, List<Coverage> coverage) {}
}
