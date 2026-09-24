package io.yak.ops.business.quality.asset;

import java.time.LocalDateTime;

/** Typed, project-scoped Quality facts for one physical table Asset Section. */
public record QualityAssetSectionSummary(
    boolean registered,
    Long monitorId,
    int monitorCount,
    int enabledMonitorCount,
    LatestExecution latestExecution) {

  public record LatestExecution(
      String executionNo,
      String lifecycleStatus,
      String result,
      int issueCount,
      LocalDateTime queuedAt,
      LocalDateTime finishedAt) {}
}
