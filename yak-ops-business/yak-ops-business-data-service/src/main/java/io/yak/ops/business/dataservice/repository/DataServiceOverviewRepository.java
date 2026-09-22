package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence port for SQL-aggregated Data Service overview reads. */
public interface DataServiceOverviewRepository {

  Snapshot load(
      LocalDateTime from,
      LocalDateTime to,
      int bucketMinutes,
      int bucketCount,
      int hotApiLimit,
      int failureLimit);

  record Snapshot(
      long apiTotal,
      long runningApis,
      long totalCalls,
      long successCalls,
      long totalDurationMs,
      long totalRows,
      List<TrendBucket> trend,
      List<ApiStatistics> hotApis,
      List<InvocationRecord> recentFailures) {

    public Snapshot {
      trend = trend == null ? List.of() : List.copyOf(trend);
      hotApis = hotApis == null ? List.of() : List.copyOf(hotApis);
      recentFailures = recentFailures == null ? List.of() : List.copyOf(recentFailures);
    }
  }

  record TrendBucket(
      int bucketIndex,
      long calls,
      long successCalls,
      long failureCalls,
      long totalDurationMs) {}

  record ApiStatistics(
      Long apiId,
      String name,
      String path,
      long calls,
      long successCalls,
      long totalDurationMs) {}
}
