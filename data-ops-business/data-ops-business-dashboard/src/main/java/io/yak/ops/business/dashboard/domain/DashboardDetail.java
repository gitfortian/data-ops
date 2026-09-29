package io.yak.ops.business.dashboard.domain;

import java.util.List;

/** Dashboard 当前草稿与历史版本聚合。 */
public record DashboardDetail(
    DashboardAsset dashboard,
    DashboardVersion currentVersion,
    Object theme,
    List<DashboardVersion> versions,
    List<DashboardWidgetSnapshot> widgets,
    List<DashboardGlobalFilterSnapshot> globalFilters,
    List<DashboardInteractionSnapshot> interactions) {

  public DashboardDetail(
      DashboardAsset dashboard,
      DashboardVersion currentVersion,
      List<DashboardVersion> versions,
      List<DashboardWidgetSnapshot> widgets,
      List<DashboardGlobalFilterSnapshot> globalFilters,
      List<DashboardInteractionSnapshot> interactions) {
    this(dashboard, currentVersion, null, versions, widgets, globalFilters, interactions);
  }
}
