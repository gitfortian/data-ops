package io.yak.ops.business.analysis.query;

public record AnalysisFilterBinding(
    String fieldId,
    AnalysisFilterOperator operator,
    Object value) {
}
