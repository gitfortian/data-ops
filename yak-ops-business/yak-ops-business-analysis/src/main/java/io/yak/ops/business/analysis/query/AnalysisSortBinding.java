package io.yak.ops.business.analysis.query;

public record AnalysisSortBinding(
    String fieldId,
    AnalysisAggregation aggregation,
    AnalysisSortDirection direction) {
}
