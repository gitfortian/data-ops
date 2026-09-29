package io.yak.ops.business.dashboard.domain;

import java.util.List;

public record GlobalFilterSpec(
    String filterKey,
    String name,
    DashboardGlobalFilterOperator operator,
    Object defaultValue,
    List<FilterBindingSpec> bindings) {
}
