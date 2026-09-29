package io.yak.ops.business.dashboard.domain;

import java.util.List;

/** Lightweight Dashboard read model for operational overview pages. */
public record DashboardOverview(
    long dashboardCount,
    long publishedDashboardCount,
    List<DashboardAsset> recentDashboards) {

  public DashboardOverview {
    recentDashboards = recentDashboards == null ? List.of() : List.copyOf(recentDashboards);
  }
}
