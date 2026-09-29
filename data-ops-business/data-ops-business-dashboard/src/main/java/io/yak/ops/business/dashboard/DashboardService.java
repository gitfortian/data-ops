package io.yak.ops.business.dashboard;

import io.yak.ops.business.dashboard.definition.DashboardManager;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardOverview;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionDetail;
import io.yak.ops.business.dashboard.publication.DashboardPublisher;
import io.yak.ops.business.dashboard.read.DashboardOverviewReader;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.business.dashboard.version.DashboardVersionManager;
import io.yak.ops.business.dashboard.version.DashboardVersionReader;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import org.springframework.stereotype.Service;

/** Stable Dashboard application facade retained for HTTP and cross-module callers. */
@Service
@ConditionalOnDataSourceEnabled
public class DashboardService {

  private final DashboardManager manager;
  private final DashboardReader reader;
  private final DashboardOverviewReader overviewReader;
  private final DashboardVersionManager versions;
  private final DashboardVersionReader versionReader;
  private final DashboardPublisher publisher;

  public DashboardService(
      DashboardManager manager,
      DashboardReader reader,
      DashboardOverviewReader overviewReader,
      DashboardVersionManager versions,
      DashboardVersionReader versionReader,
      DashboardPublisher publisher) {
    this.manager = manager;
    this.reader = reader;
    this.overviewReader = overviewReader;
    this.versions = versions;
    this.versionReader = versionReader;
    this.publisher = publisher;
  }

  public List<DashboardAsset> list() {
    return reader.list();
  }

  public DashboardOverview overview(int listLimit) {
    return overviewReader.overview(listLimit);
  }

  public DashboardDetail get(long dashboardId) {
    return reader.get(dashboardId);
  }

  public List<DashboardVersion> versions(long dashboardId) {
    return versionReader.versions(dashboardId);
  }

  public DashboardVersionDetail version(long dashboardId, int versionNo) {
    return versionReader.version(dashboardId, versionNo);
  }

  public DashboardVersionDetail published(long dashboardId) {
    return versionReader.published(dashboardId);
  }

  public DashboardDetail create(DashboardDraft draft) {
    return manager.create(draft);
  }

  public DashboardDetail saveVersion(long dashboardId, DashboardDraft draft) {
    return versions.saveVersion(dashboardId, draft);
  }

  public DashboardDetail publish(long dashboardId) {
    return publisher.publish(dashboardId);
  }

  public DashboardDetail restoreVersion(long dashboardId, int versionNo) {
    return versions.restoreVersion(dashboardId, versionNo);
  }

  /** @deprecated Use {@link #restoreVersion(long, int)}. */
  @Deprecated
  public DashboardDetail activateVersion(long dashboardId, int versionNo) {
    return restoreVersion(dashboardId, versionNo);
  }

  public void delete(long dashboardId) {
    manager.delete(dashboardId);
  }
}
