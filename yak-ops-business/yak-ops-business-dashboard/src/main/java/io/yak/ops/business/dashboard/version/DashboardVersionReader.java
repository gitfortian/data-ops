package io.yak.ops.business.dashboard.version;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionDetail;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side entry for immutable DashboardVersion snapshots. */
@Component
public class DashboardVersionReader {

  private final DashboardReader dashboards;
  private final DashboardVersionRepository versions;

  public DashboardVersionReader(
      DashboardReader dashboards,
      DashboardVersionRepository versions) {
    this.dashboards = dashboards;
    this.versions = versions;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public List<DashboardVersion> versions(long dashboardId) {
    dashboards.require(dashboardId);
    return versions.listVersions(dashboardId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public DashboardVersionDetail version(long dashboardId, int versionNo) {
    DashboardAsset dashboard = dashboards.require(dashboardId);
    if (versionNo <= 0) {
      throw new IllegalArgumentException("versionNo 必须大于 0");
    }
    DashboardVersionSnapshot snapshot = versions.findVersionSnapshotByNo(dashboardId, versionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "DashboardVersion 不存在：V" + versionNo));
    return detail(dashboard, snapshot);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public DashboardVersionDetail published(long dashboardId) {
    DashboardAsset dashboard = dashboards.require(dashboardId);
    if (dashboard.publishedVersionId() == null) {
      throw new IllegalStateException("Dashboard 尚未发布：" + dashboardId);
    }
    DashboardVersionSnapshot snapshot = versions.findVersionSnapshot(dashboard.publishedVersionId())
        .orElseThrow(() -> new IllegalStateException(
            "Dashboard 已发布版本不存在：" + dashboard.publishedVersionId()));
    return detail(dashboard, snapshot);
  }

  public DashboardVersionSnapshot requireSnapshot(long versionId) {
    return versions.findVersionSnapshot(versionId)
        .orElseThrow(() -> new IllegalStateException("DashboardVersion 不存在：" + versionId));
  }

  private DashboardVersionDetail detail(
      DashboardAsset dashboard,
      DashboardVersionSnapshot snapshot) {
    return new DashboardVersionDetail(
        dashboard,
        snapshot.version(),
        snapshot.theme(),
        snapshot.widgets(),
        snapshot.globalFilters(),
        snapshot.interactions());
  }
}
