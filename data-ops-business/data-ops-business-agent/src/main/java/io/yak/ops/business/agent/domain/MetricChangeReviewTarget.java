package io.yak.ops.business.agent.domain;

/** Frozen source selection and prepared evidence digest; neither grants authority. */
public record MetricChangeReviewTarget(long metricId, int version, int publishedVersion,
    long publicationEventId, String definition, String businessQuestion) {
  public MetricChangeReviewTarget {
    if (metricId <= 0 || version <= 0 || publishedVersion <= 0 || publicationEventId <= 0
        || definition == null || !definition.matches("[a-f0-9]{64}")
        || businessQuestion == null || businessQuestion.length() > 512) {
      throw new IllegalArgumentException("版本变更核对目标无效");
    }
  }
}
