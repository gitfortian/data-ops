package io.yak.ops.business.dashboard.domain;

import java.util.List;

/** Dashboard 全局筛选器版本快照。 */
public record DashboardGlobalFilterSnapshot(
    String filterKey,
    String name,
    DashboardGlobalFilterOperator operator,
    Object defaultValue,
    List<DashboardGlobalFilterBindingSnapshot> bindings,
    int sortOrder) {
}
