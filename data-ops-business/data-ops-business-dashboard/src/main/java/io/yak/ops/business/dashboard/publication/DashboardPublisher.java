package io.yak.ops.business.dashboard.publication;

import io.yak.ops.business.dashboard.change.DashboardChangedEvent;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepository;
import io.yak.ops.core.project.CurrentProject;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Moves the published pointer to the current immutable DashboardVersion. */
@Component
public class DashboardPublisher {

  private final DashboardReader reader;
  private final DashboardRepository dashboards;
  private final DashboardVersionRepository versions;
  private final CurrentProject currentProject;
  private final ApplicationEventPublisher events;

  public DashboardPublisher(
      DashboardReader reader,
      DashboardRepository dashboards,
      DashboardVersionRepository versions,
      CurrentProject currentProject,
      ApplicationEventPublisher events) {
    this.reader = reader;
    this.dashboards = dashboards;
    this.versions = versions;
    this.currentProject = currentProject;
    this.events = events;
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail publish(long dashboardId) {
    long projectId = currentProject.requireProjectId();
    DashboardAsset dashboard = reader.require(dashboardId);
    if (dashboard.currentVersionId() == null || dashboard.currentVersionNo() <= 0) {
      throw new IllegalStateException("Dashboard 没有可发布的草稿：" + dashboardId);
    }
    if (Objects.equals(dashboard.currentVersionId(), dashboard.publishedVersionId())) {
      return reader.get(dashboardId);
    }

    DashboardVersionSnapshot current = versions.findVersionSnapshot(dashboard.currentVersionId())
        .orElseThrow(() -> new IllegalStateException(
            "Dashboard 当前草稿版本不存在：" + dashboard.currentVersionId()));
    dashboards.updatePublishedVersion(
        dashboardId,
        current.version().id(),
        current.version().versionNo());
    events.publishEvent(DashboardChangedEvent.refreshed(projectId, dashboardId));
    return reader.get(dashboardId);
  }
}
