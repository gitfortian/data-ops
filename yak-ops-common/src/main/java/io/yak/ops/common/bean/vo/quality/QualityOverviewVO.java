package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 数据质量总览响应契约。 */
public final class QualityOverviewVO {
  private QualityOverviewVO() {
  }

  public record Overview(
      LocalDate rangeStart,
      LocalDate rangeEnd,
      Summary summary,
      List<Dimension> dimensions,
      List<IssueContributor> issueContributors,
      List<TrendPoint> trend) {
  }

  public record Summary(
      long executionCount,
      long activeMonitorCount,
      long executedRuleCount,
      long passedRuleCount,
      long failedRuleCount,
      long errorRuleCount,
      long issueRuleCount,
      long issueExecutionCount,
      long affectedMonitorCount,
      long affectedTableCount,
      long affectedColumnCount,
      Double passRate,
      Double issueRate,
      Double averageDurationMs,
      @QualityDateTimeFormat LocalDateTime latestExecutionAt) {
  }

  public record Dimension(
      String dimension,
      long total,
      long issues,
      Double passRate) {
  }

  public record IssueContributor(
      String dimension,
      long issues,
      Double ratio) {
  }

  public record TrendPoint(
      LocalDate date,
      long executionCount,
      long activeMonitorCount,
      long executedRuleCount,
      long passedRuleCount,
      long failedRuleCount,
      long errorRuleCount,
      long issueExecutionCount,
      Double passRate,
      Double issueRate,
      Double averageDurationMs) {
  }
}
