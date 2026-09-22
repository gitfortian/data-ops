package io.yak.ops.business.dataset;

public record DatasetQueryColumnBinding(
    String key,
    String fieldId,
    String displayName,
    DatasetFieldDataType dataType,
    DatasetAggregation aggregation) {
}
