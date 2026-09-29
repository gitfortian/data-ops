package io.yak.ops.business.dashboard.change;

/** Committed Dashboard mutation fact consumed by derived projections. */
public record DashboardChangedEvent(long projectId, long dashboardId, boolean deleted) {

  public DashboardChangedEvent {
    if (projectId <= 0L) {
      throw new IllegalArgumentException("projectId 必须大于 0");
    }
    if (dashboardId <= 0L) {
      throw new IllegalArgumentException("dashboardId 必须大于 0");
    }
  }

  public static DashboardChangedEvent refreshed(long projectId, long dashboardId) {
    return new DashboardChangedEvent(projectId, dashboardId, false);
  }

  public static DashboardChangedEvent deleted(long projectId, long dashboardId) {
    return new DashboardChangedEvent(projectId, dashboardId, true);
  }
}
