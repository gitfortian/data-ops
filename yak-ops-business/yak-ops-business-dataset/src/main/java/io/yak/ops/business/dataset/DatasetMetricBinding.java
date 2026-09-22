package io.yak.ops.business.dataset;

public record DatasetMetricBinding(
    String fieldId,
    DatasetAggregation aggregation) {
}
