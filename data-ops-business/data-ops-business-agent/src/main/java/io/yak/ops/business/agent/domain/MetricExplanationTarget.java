package io.yak.ops.business.agent.domain;

public record MetricExplanationTarget(long metricId, int version, String businessQuestion) {
  public MetricExplanationTarget {
    if (metricId <= 0 || version <= 0 || businessQuestion == null || businessQuestion.length() > 512) {
      throw new IllegalArgumentException("指标版本或业务问题无效");
    }
  }
}
