package io.yak.ops.business.dataset;

import java.util.List;

public record DatasetFilter(
    String fieldId,
    DatasetFilterOperator operator,
    Object value,
    List<Object> values) {
}
