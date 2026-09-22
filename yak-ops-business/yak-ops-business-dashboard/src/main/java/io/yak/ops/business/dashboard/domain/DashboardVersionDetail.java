package io.yak.ops.business.dashboard.domain;

import java.util.List;

/** 指定 DashboardVersion 的完整快照。 */
public record DashboardVersionDetail(
    DashboardAsset dashboard,
    DashboardVersion version,
    Object theme,
    List<DashboardWidgetSnapshot> widgets,
    List<DashboardGlobalFilterSnapshot> globalFilters,
    List<DashboardInteractionSnapshot> interactions) {

  public DashboardVersionDetail(
      DashboardAsset dashboard,
      DashboardVersion version,
      List<DashboardWidgetSnapshot> widgets,
      List<DashboardGlobalFilterSnapshot> globalFilters,
      List<DashboardInteractionSnapshot> interactions) {
    this(dashboard, version, null, widgets, globalFilters, interactions);
  }
}
