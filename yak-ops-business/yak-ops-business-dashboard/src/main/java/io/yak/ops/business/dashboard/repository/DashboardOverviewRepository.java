package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import java.util.List;

/** Persistence port for bounded Dashboard overview reads. */
public interface DashboardOverviewRepository {

  Summary summarize();

  List<DashboardAsset> listRecent(int limit);

  record Summary(long dashboardCount, long publishedDashboardCount) {}
}
