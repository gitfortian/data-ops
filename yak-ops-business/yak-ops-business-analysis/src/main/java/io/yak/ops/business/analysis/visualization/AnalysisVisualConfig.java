package io.yak.ops.business.analysis.visualization;

public record AnalysisVisualConfig(
    boolean showLegend,
    boolean showDataLabels,
    boolean smooth,
    boolean showGrid) {
}
