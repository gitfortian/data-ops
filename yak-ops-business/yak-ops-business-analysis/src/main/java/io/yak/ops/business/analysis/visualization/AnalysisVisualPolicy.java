package io.yak.ops.business.analysis.visualization;

import java.util.Objects;
import org.springframework.stereotype.Component;

/** Owns chart-local visual defaults; Dashboard layout is intentionally out of scope. */
@Component
public class AnalysisVisualPolicy {

  public AnalysisVisualConfig normalize(AnalysisVisualConfig config, AnalysisChartType chartType) {
    if (config != null) return config;
    AnalysisChartType type = Objects.requireNonNull(chartType, "chartType");
    return new AnalysisVisualConfig(
        type == AnalysisChartType.PIE,
        false,
        type == AnalysisChartType.LINE,
        type == AnalysisChartType.BAR || type == AnalysisChartType.LINE);
  }
}
