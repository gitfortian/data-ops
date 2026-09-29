package io.yak.ops.business.analysis.domain;

import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;

/** Normalized current definition ready to cross the Analysis repository boundary. */
public record AnalysisDefinition(
    String name,
    String description,
    long datasetId,
    AnalysisChartType chartType,
    AnalysisQuerySpec querySpec,
    AnalysisVisualConfig visualConfig) {
}
