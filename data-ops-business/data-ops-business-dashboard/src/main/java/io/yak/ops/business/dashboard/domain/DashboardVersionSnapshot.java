package io.yak.ops.business.dashboard.domain;

import java.util.List;

/** Repository 组装出的完整 DashboardVersion 聚合快照。 */
public record DashboardVersionSnapshot(
    DashboardVersion version,
    Object theme,
    List<DashboardWidgetSnapshot> widgets,
    List<DashboardGlobalFilterSnapshot> globalFilters,
    List<DashboardInteractionSnapshot> interactions) {
}
