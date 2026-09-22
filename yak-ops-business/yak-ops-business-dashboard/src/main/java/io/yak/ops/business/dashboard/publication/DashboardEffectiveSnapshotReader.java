package io.yak.ops.business.dashboard.publication;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the effective Dashboard snapshot: published when present, otherwise current draft. */
@Component
public class DashboardEffectiveSnapshotReader {

  private final DashboardReader dashboards;
  private final DashboardVersionRepository versions;

  public DashboardEffectiveSnapshotReader(
      DashboardReader dashboards,
      DashboardVersionRepository versions) {
    this.dashboards = dashboards;
    this.versions = versions;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public EffectiveSnapshot read(long dashboardId) {
    DashboardAsset dashboard = dashboards.require(dashboardId);
    if (dashboard.publishedVersionId() != null) {
      return new EffectiveSnapshot(
          dashboard,
          require(dashboard.publishedVersionId(), "Dashboard 已发布版本不存在"),
          true);
    }
    if (dashboard.currentVersionId() != null) {
      return new EffectiveSnapshot(
          dashboard,
          require(dashboard.currentVersionId(), "Dashboard 当前草稿版本不存在"),
          false);
    }
    return new EffectiveSnapshot(dashboard, null, false);
  }

  private DashboardVersionSnapshot require(long versionId, String message) {
    return versions.findVersionSnapshot(versionId)
        .orElseThrow(() -> new IllegalStateException(message + "：" + versionId));
  }

  public record EffectiveSnapshot(
      DashboardAsset dashboard,
      DashboardVersionSnapshot snapshot,
      boolean published) {
  }
}
