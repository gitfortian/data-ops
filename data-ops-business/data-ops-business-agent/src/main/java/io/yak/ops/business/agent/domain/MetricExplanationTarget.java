package io.yak.ops.business.agent.domain;

public record MetricExplanationTarget(long metricId, int version, String businessQuestion, String view) {
  public MetricExplanationTarget(long metricId, int version, String businessQuestion) {
    this(metricId, version, businessQuestion, null);
  }
  public MetricExplanationTarget {
    if (metricId <= 0 || version <= 0 || businessQuestion == null || businessQuestion.length() > 512
        || (view != null && !"SNAPSHOT".equals(view))) {
      throw new IllegalArgumentException("指标版本或业务问题无效");
    }
  }
}
