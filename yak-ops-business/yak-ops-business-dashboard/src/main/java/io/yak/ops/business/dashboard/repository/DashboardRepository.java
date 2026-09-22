package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import java.util.List;
import java.util.Optional;

/** Persistence port for Dashboard identity and current/published version pointers. */
public interface DashboardRepository {

  long insertDashboard(String name, String description);

  void updateCurrentVersion(
      long dashboardId,
      long versionId,
      int versionNo,
      String name,
      String description);

  void updatePublishedVersion(long dashboardId, long versionId, int versionNo);

  Optional<DashboardAsset> findDashboard(long dashboardId);

  List<DashboardAsset> listDashboards();

  void deleteDashboard(long dashboardId);
}
