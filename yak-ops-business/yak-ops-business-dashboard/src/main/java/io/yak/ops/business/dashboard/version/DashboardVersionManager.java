package io.yak.ops.business.dashboard.version;

import io.yak.ops.business.dashboard.change.DashboardChangedEvent;
import io.yak.ops.business.dashboard.composition.DashboardCompositionNormalizer;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardVersionDetail;
import io.yak.ops.business.dashboard.domain.FilterBindingSpec;
import io.yak.ops.business.dashboard.domain.GlobalFilterSpec;
import io.yak.ops.business.dashboard.domain.InteractionSpec;
import io.yak.ops.business.dashboard.domain.WidgetSpec;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns save and restore transitions for immutable DashboardVersion snapshots. */
@Component
public class DashboardVersionManager {

  private final DashboardReader dashboards;
  private final DashboardVersionReader versions;
  private final DashboardCompositionNormalizer composition;
  private final DashboardVersionAppender appender;
  private final CurrentProject currentProject;
  private final ApplicationEventPublisher events;

  public DashboardVersionManager(
      DashboardReader dashboards,
      DashboardVersionReader versions,
      DashboardCompositionNormalizer composition,
      DashboardVersionAppender appender,
      CurrentProject currentProject,
      ApplicationEventPublisher events) {
    this.dashboards = dashboards;
    this.versions = versions;
    this.composition = composition;
    this.appender = appender;
    this.currentProject = currentProject;
    this.events = events;
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail saveVersion(long dashboardId, DashboardDraft draft) {
    long projectId = currentProject.requireProjectId();
    dashboards.require(dashboardId);
    DashboardDraft normalized = composition.normalize(draft);
    appender.appendNext(dashboardId, normalized);
    events.publishEvent(DashboardChangedEvent.refreshed(projectId, dashboardId));
    return dashboards.get(dashboardId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail restoreVersion(long dashboardId, int versionNo) {
    DashboardVersionDetail source = versions.version(dashboardId, versionNo);
    return saveVersion(dashboardId, fromVersion(source));
  }

  private DashboardDraft fromVersion(DashboardVersionDetail detail) {
    List<WidgetSpec> widgets = detail.widgets().stream()
        .map(widget -> new WidgetSpec(
            widget.widgetKey(),
            widget.analysisId(),
            widget.title(),
            widget.inlineAnalysis(),
            widget.x(),
            widget.y(),
            widget.w(),
            widget.h(),
            widget.minW(),
            widget.minH()))
        .toList();

    List<GlobalFilterSpec> filters = detail.globalFilters().stream()
        .map(filter -> new GlobalFilterSpec(
            filter.filterKey(),
            filter.name(),
            filter.operator(),
            filter.defaultValue(),
            filter.bindings().stream()
                .map(binding -> new FilterBindingSpec(binding.widgetKey(), binding.fieldId()))
                .toList()))
        .toList();

    List<InteractionSpec> interactions = detail.interactions().stream()
        .map(interaction -> new InteractionSpec(
            interaction.interactionKey(),
            interaction.event(),
            interaction.sourceWidgetKey(),
            interaction.sourceFieldId(),
            interaction.targetFilterKey()))
        .toList();

    return new DashboardDraft(
        detail.version().name(),
        detail.version().description(),
        detail.version().activeDatasetId(),
        detail.theme(),
        widgets,
        filters,
        interactions);
  }
}
