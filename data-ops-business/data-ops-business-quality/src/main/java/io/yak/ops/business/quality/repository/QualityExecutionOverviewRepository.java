package io.yak.ops.business.quality.repository;

import java.time.LocalDateTime;
import java.util.List;

/** 数据质量 execution 运行总览持久化读模型 contract。 */
public interface QualityExecutionOverviewRepository {

  Overview overview(LocalDateTime start, LocalDateTime end, boolean hourlyTrend);

  /** Lightweight metrics-only path for first-screen summaries. */
  default Metrics metrics(LocalDateTime start, LocalDateTime end) {
    Overview overview = overview(start, end, false);
    return overview == null || overview.metrics() == null ? Metrics.empty() : overview.metrics();
  }

  Execution latest(LocalDateTime start, LocalDateTime end);

  TaskSummary taskSummary(String taskId, LocalDateTime start, LocalDateTime end);

  List<TaskSummary> recentTasks(LocalDateTime start, LocalDateTime end, int limit);

  record Overview(Metrics metrics, List<TrendPoint> trend, Execution latest) {
    public Overview {
      trend = trend == null ? List.of() : List.copyOf(trend);
    }
  }

  record Metrics(
      long successCount,
      long runningCount,
      long failedCount,
      long scheduleCount,
      long processedRecords,
      long durationTotalMs,
      long durationSampleCount) {
    public static Metrics empty() {
      return new Metrics(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public Metrics withRunning(long value) {
      return new Metrics(
          successCount,
          value,
          failedCount,
          scheduleCount,
          processedRecords,
          durationTotalMs,
          durationSampleCount);
    }
  }

  record TrendPoint(LocalDateTime bucket, long count) {}

  record Execution(
      String taskId,
      String taskName,
      String status,
      LocalDateTime occurredAt,
      long durationMs,
      String executionId) {}

  record TaskSummary(
      String taskId,
      String taskName,
      LocalDateTime lastRunTime,
      long runCount,
      long successCount,
      long failedCount,
      long lastDurationMs,
      String lastStatus,
      String executionId) {}
}
