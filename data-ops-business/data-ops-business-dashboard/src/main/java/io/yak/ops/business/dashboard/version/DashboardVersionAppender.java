package io.yak.ops.business.dashboard.version;

import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepository;
import org.springframework.stereotype.Component;

/** Appends one immutable DashboardVersion and advances the current-version pointer. */
@Component
public class DashboardVersionAppender {

  private final DashboardVersionRepository versions;
  private final DashboardRepository dashboards;

  public DashboardVersionAppender(
      DashboardVersionRepository versions,
      DashboardRepository dashboards) {
    this.versions = versions;
    this.dashboards = dashboards;
  }

  public long append(long dashboardId, int versionNo, DashboardDraft draft) {
    if (versionNo <= 0) {
      throw new IllegalArgumentException("versionNo 必须大于 0");
    }
    long versionId = versions.appendVersion(dashboardId, versionNo, draft);
    dashboards.updateCurrentVersion(
        dashboardId,
        versionId,
        versionNo,
        draft.name(),
        draft.description());
    return versionId;
  }

  public long appendNext(long dashboardId, DashboardDraft draft) {
    return append(dashboardId, versions.nextVersionNo(dashboardId), draft);
  }
}
