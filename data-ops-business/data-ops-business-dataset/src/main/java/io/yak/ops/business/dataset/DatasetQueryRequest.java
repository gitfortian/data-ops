package io.yak.ops.business.dataset;

import java.util.List;

public record DatasetQueryRequest(
    Integer versionNo,
    List<String> dimensions,
    List<DatasetMetricBinding> metrics,
    List<DatasetFilter> filters,
    List<DatasetSort> sorts,
    Integer limit,
    Integer timeoutSeconds) {
}
