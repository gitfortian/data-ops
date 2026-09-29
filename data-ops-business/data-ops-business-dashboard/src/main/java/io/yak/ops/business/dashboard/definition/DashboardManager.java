package io.yak.ops.business.dashboard.definition;

import io.yak.ops.business.dashboard.change.DashboardChangedEvent;
import io.yak.ops.business.dashboard.composition.DashboardCompositionNormalizer;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.version.DashboardVersionAppender;
import io.yak.ops.core.project.CurrentProject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns Dashboard identity lifecycle; version lifecycle remains delegated to version roles. */
@Component
public class DashboardManager {

  private final DashboardRepository dashboards;
  private final DashboardCompositionNormalizer composition;
  private final DashboardVersionAppender versions;
  private final DashboardReader reader;
  private final CurrentProject currentProject;
  private final ApplicationEventPublisher events;

  public DashboardManager(
      DashboardRepository dashboards,
      DashboardCompositionNormalizer composition,
      DashboardVersionAppender versions,
      DashboardReader reader,
      CurrentProject currentProject,
      ApplicationEventPublisher events) {
    this.dashboards = dashboards;
    this.composition = composition;
    this.versions = versions;
    this.reader = reader;
    this.currentProject = currentProject;
    this.events = events;
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail create(DashboardDraft draft) {
    long projectId = currentProject.requireProjectId();
    DashboardDraft normalized = composition.normalize(draft);
    long dashboardId = dashboards.insertDashboard(normalized.name(), normalized.description());
    versions.append(dashboardId, 1, normalized);
    events.publishEvent(DashboardChangedEvent.refreshed(projectId, dashboardId));
    return reader.get(dashboardId);
  }

  @Transactional("yakBusinessTransactionManager")
  public void delete(long dashboardId) {
    long projectId = currentProject.requireProjectId();
    reader.require(dashboardId);
    dashboards.deleteDashboard(dashboardId);
    events.publishEvent(DashboardChangedEvent.deleted(projectId, dashboardId));
  }
}
