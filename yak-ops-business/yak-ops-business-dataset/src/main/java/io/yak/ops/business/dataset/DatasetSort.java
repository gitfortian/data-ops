package io.yak.ops.business.dataset;

public record DatasetSort(
    String fieldId,
    DatasetAggregation aggregation,
    DatasetSortDirection direction) {
}
