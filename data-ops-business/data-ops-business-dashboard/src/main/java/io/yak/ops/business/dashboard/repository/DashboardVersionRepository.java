package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import java.util.List;
import java.util.Optional;

/** Persistence port for immutable DashboardVersion snapshots. */
public interface DashboardVersionRepository {

  long appendVersion(long dashboardId, int versionNo, DashboardDraft draft);

  Optional<DashboardVersionSnapshot> findVersionSnapshot(long versionId);

  Optional<DashboardVersionSnapshot> findVersionSnapshotByNo(long dashboardId, int versionNo);

  List<DashboardVersion> listVersions(long dashboardId);

  int nextVersionNo(long dashboardId);
}
