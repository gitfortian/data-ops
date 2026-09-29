package io.yak.ops.business.quality.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 数据质量总览读模型 Repository。 */
public interface QualityOverviewRepository {

  OverviewStats stats(
      LocalDateTime todayStart,
      LocalDateTime todayEnd,
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd);

  AnalyticsStats analyticsStats(LocalDateTime rangeStart, LocalDateTime rangeEnd);

  List<DimensionSummary> dimensions(LocalDateTime rangeStart, LocalDateTime rangeEnd);

  List<TrendSummary> trend(LocalDateTime rangeStart, LocalDateTime rangeEnd);

  List<IssueSummary> recentIssues(
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd,
      int limit);

  record OverviewStats(
      long monitoredTableCount,
      long enabledRuleCount,
      long todayExecutionCount,
      long todayIssueTableCount,
      long recentExecutedRuleCount,
      long recentPassedRuleCount,
      long recentIssueRuleCount) {
  }

  record AnalyticsStats(
      long executionCount,
      long activeMonitorCount,
      long executedRuleCount,
      long passedRuleCount,
      long failedRuleCount,
      long errorRuleCount,
      long issueExecutionCount,
      long affectedMonitorCount,
      long affectedTableCount,
      long affectedColumnCount,
      Double averageDurationMs,
      LocalDateTime latestExecutionAt) {
  }

  record DimensionSummary(
      String dimension,
      long total,
      long passed,
      long issues) {
  }

  record TrendSummary(
      LocalDate date,
      long executionCount,
      long activeMonitorCount,
      long executedRuleCount,
      long passedRuleCount,
      long failedRuleCount,
      long errorRuleCount,
      long issueExecutionCount,
      Double averageDurationMs) {
  }

  record IssueSummary(
      long id,
      String executionNo,
      long monitorId,
      String monitorName,
      String objectName,
      String tableName,
      String ruleName,
      String dimension,
      String columnName,
      String checkResult,
      LocalDateTime queuedAt) {
  }
}
