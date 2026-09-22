package io.yak.ops.business.quality.repository;

import java.util.List;

/** Explicit system-level scan used only to restore scheduled monitors by persisted Project. */
public interface QualityScheduleRecoveryRepository {
  List<ProjectMonitorRef> listScheduledMonitors();

  record ProjectMonitorRef(long projectId, long monitorId) {
    public ProjectMonitorRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId must be positive");
      if (monitorId <= 0L) throw new IllegalArgumentException("monitorId must be positive");
    }
  }
}
