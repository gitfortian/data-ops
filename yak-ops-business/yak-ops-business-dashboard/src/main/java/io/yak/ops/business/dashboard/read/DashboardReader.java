package io.yak.ops.business.dashboard.read;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Shared read-side entry for Dashboard identity and the current draft snapshot. */
@Component
public class DashboardReader {

  private final DashboardRepository dashboards;
  private final DashboardVersionRepository versions;

  public DashboardReader(
      DashboardRepository dashboards,
      DashboardVersionRepository versions) {
    this.dashboards = dashboards;
    this.versions = versions;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public List<DashboardAsset> list() {
    return dashboards.listDashboards();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public DashboardAsset require(long dashboardId) {
    requireDashboardId(dashboardId);
    return dashboards.findDashboard(dashboardId)
        .orElseThrow(() -> new IllegalArgumentException("Dashboard 不存在：" + dashboardId));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public DashboardDetail get(long dashboardId) {
    DashboardAsset dashboard = require(dashboardId);
    DashboardVersionSnapshot snapshot = dashboard.currentVersionId() == null
        ? null
        : versions.findVersionSnapshot(dashboard.currentVersionId())
            .orElseThrow(() -> new IllegalStateException(
                "Dashboard 当前草稿版本不存在：" + dashboard.currentVersionId()));

    DashboardVersion current = snapshot == null ? null : snapshot.version();
    return new DashboardDetail(
        dashboard,
        current,
        snapshot == null ? null : snapshot.theme(),
        versions.listVersions(dashboardId),
        snapshot == null ? List.of() : snapshot.widgets(),
        snapshot == null ? List.of() : snapshot.globalFilters(),
        snapshot == null ? List.of() : snapshot.interactions());
  }

  public static void requireDashboardId(long dashboardId) {
    if (dashboardId <= 0L) {
      throw new IllegalArgumentException("dashboardId 必须大于 0");
    }
  }
}
