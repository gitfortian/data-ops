package io.yak.ops.business.dashboard.dao.model;

import lombok.Data;

/** Aggregate projection for the Dashboard overview query. */
@Data
public class DashboardOverviewSummaryPO {
  private Long dashboardCount;
  private Long publishedDashboardCount;
}
