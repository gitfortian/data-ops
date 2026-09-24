package io.yak.ops.business.quality.asset;

import io.yak.ops.spi.section.SectionSummary;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed, project-scoped Quality facts for one physical table Asset Section. */
public record QualityAssetSectionSummary(
    boolean registered,
    Long monitorId,
    int monitorCount,
    int enabledMonitorCount,
    LatestExecution latestExecution) implements SectionSummary {

  @Override
  public Map<String, Object> values() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("registered", registered);
    values.put("monitorId", monitorId == null ? "" : monitorId);
    values.put("monitorCount", monitorCount);
    values.put("enabledMonitorCount", enabledMonitorCount);
    values.put("latestExecution", latestExecution == null
        ? Map.of("status", "NOT_RUN") : latestExecution);
    return Map.copyOf(values);
  }

  public record LatestExecution(
      String executionNo,
      String lifecycleStatus,
      String result,
      int issueCount,
      LocalDateTime queuedAt,
      LocalDateTime finishedAt) {}
}
