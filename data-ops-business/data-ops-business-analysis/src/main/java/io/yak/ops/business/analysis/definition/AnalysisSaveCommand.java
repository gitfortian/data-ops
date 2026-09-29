package io.yak.ops.business.analysis.definition;

import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;

/** Immutable mutation input independent of HTTP transport models. */
public record AnalysisSaveCommand(
    String name,
    String description,
    long datasetId,
    AnalysisChartType chartType,
    AnalysisQuerySpec querySpec,
    AnalysisVisualConfig visualConfig) {
}
