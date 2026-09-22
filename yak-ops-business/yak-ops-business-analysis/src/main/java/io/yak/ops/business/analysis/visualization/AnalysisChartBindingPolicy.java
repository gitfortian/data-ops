package io.yak.ops.business.analysis.visualization;

import java.util.Objects;
import org.springframework.stereotype.Component;

/** Chart-specific cardinality rules for dimensions and metrics. */
@Component
public class AnalysisChartBindingPolicy {

  public void validate(AnalysisChartType chartType, int dimensionCount, int metricCount) {
    Objects.requireNonNull(chartType, "chartType");
    switch (chartType) {
      case METRIC -> {
        if (dimensionCount != 0) throw new IllegalArgumentException("指标卡不能配置维度");
        if (metricCount != 1) throw new IllegalArgumentException("指标卡必须且只能配置 1 个指标");
      }
      case PIE -> {
        if (dimensionCount != 1) throw new IllegalArgumentException("饼图必须且只能配置 1 个维度");
        if (metricCount != 1) throw new IllegalArgumentException("饼图必须且只能配置 1 个指标");
      }
      case BAR, LINE -> {
        if (dimensionCount == 0) throw new IllegalArgumentException("柱状图/折线图至少需要 1 个维度");
        if (metricCount == 0) throw new IllegalArgumentException("柱状图/折线图至少需要 1 个指标");
      }
      case TABLE -> {
        if (dimensionCount == 0 && metricCount == 0) {
          throw new IllegalArgumentException("表格至少需要 1 个维度或指标");
        }
      }
    }
  }
}
