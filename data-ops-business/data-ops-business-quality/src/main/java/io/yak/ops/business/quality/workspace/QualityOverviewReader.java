package io.yak.ops.business.quality.workspace;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.repository.QualityOverviewRepository;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.AnalyticsStats;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.DimensionSummary;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.IssueSummary;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.OverviewStats;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.TrendSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 数据质量健康度、指标趋势和问题贡献读模型。 */
@Component
@ConditionalOnQualityEnabled
public class QualityOverviewReader {

  private static final int RANGE_DAYS = 7;
  private static final int MAX_ANALYTICS_RANGE_DAYS = 90;
  private static final int HOME_RECENT_ISSUE_LIMIT = 4;
  private static final int ISSUE_CONTRIBUTOR_LIMIT = 3;

  private final QualityOverviewRepository repository;

  public QualityOverviewReader(QualityOverviewRepository repository) {
    this.repository = repository;
  }

  /** 首页兼容读模型，保持原有 7 日口径。 */
  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Overview overview() {
    LocalDate today = LocalDate.now();
    LocalDate rangeStartDate = today.minusDays(RANGE_DAYS - 1L);
    LocalDateTime todayStart = today.atStartOfDay();
    LocalDateTime rangeStart = rangeStartDate.atStartOfDay();
    LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();

    OverviewStats stats = repository.stats(todayStart, rangeEnd, rangeStart, rangeEnd);
    List<DimensionHealth> dimensions = repository.dimensions(rangeStart, rangeEnd).stream()
        .filter(item -> item.total() > 0L)
        .map(this::dimension)
        .toList();
    List<RecentIssue> recentIssues = repository.recentIssues(
            rangeStart, rangeEnd, HOME_RECENT_ISSUE_LIMIT).stream()
        .map(this::issue)
        .toList();

    return new Overview(
        rangeStartDate,
        today,
        rate(stats.recentPassedRuleCount(), stats.recentExecutedRuleCount()),
        stats.monitoredTableCount(),
        stats.enabledRuleCount(),
        stats.todayExecutionCount(),
        stats.todayIssueTableCount(),
        stats.recentIssueRuleCount(),
        dimensions,
        recentIssues);
  }

  /**
   * 质量总览页读模型。日期区间按自然日闭区间解释，并限制在 90 天内，避免总览查询失控。
   */
  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public AnalyticsOverview analytics(LocalDate requestedStart, LocalDate requestedEnd) {
    DateRange range = resolveRange(requestedStart, requestedEnd);
    LocalDateTime rangeStart = range.start().atStartOfDay();
    LocalDateTime rangeEnd = range.end().plusDays(1).atStartOfDay();

    AnalyticsStats persistedStats = repository.analyticsStats(rangeStart, rangeEnd);
    List<DimensionHealth> dimensions = repository.dimensions(rangeStart, rangeEnd).stream()
        .filter(item -> item.total() > 0L)
        .map(this::dimension)
        .toList();

    long totalIssues = dimensions.stream().mapToLong(DimensionHealth::issues).sum();
    List<IssueContributor> contributors = dimensions.stream()
        .filter(item -> item.issues() > 0L)
        .sorted((left, right) -> Long.compare(right.issues(), left.issues()))
        .limit(ISSUE_CONTRIBUTOR_LIMIT)
        .map(item -> new IssueContributor(
            item.dimension(), item.issues(), rate(item.issues(), totalIssues)))
        .toList();

    long issueRules = persistedStats.failedRuleCount() + persistedStats.errorRuleCount();
    AnalyticsSummary summary = new AnalyticsSummary(
        persistedStats.executionCount(),
        persistedStats.activeMonitorCount(),
        persistedStats.executedRuleCount(),
        persistedStats.passedRuleCount(),
        persistedStats.failedRuleCount(),
        persistedStats.errorRuleCount(),
        issueRules,
        persistedStats.issueExecutionCount(),
        persistedStats.affectedMonitorCount(),
        persistedStats.affectedTableCount(),
        persistedStats.affectedColumnCount(),
        rate(persistedStats.passedRuleCount(), persistedStats.executedRuleCount()),
        rate(issueRules, persistedStats.executedRuleCount()),
        persistedStats.averageDurationMs(),
        persistedStats.latestExecutionAt());

    return new AnalyticsOverview(
        range.start(),
        range.end(),
        summary,
        dimensions,
        contributors,
        fillTrend(range, repository.trend(rangeStart, rangeEnd)));
  }

  private List<TrendPoint> fillTrend(DateRange range, List<TrendSummary> persisted) {
    Map<LocalDate, TrendSummary> byDate = persisted.stream()
        .collect(Collectors.toMap(TrendSummary::date, Function.identity(), (left, right) -> right));
    List<TrendPoint> result = new ArrayList<>();
    for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
      TrendSummary item = byDate.get(date);
      if (item == null) {
        result.add(new TrendPoint(date, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null, null));
        continue;
      }
      long issues = item.failedRuleCount() + item.errorRuleCount();
      result.add(new TrendPoint(
          date,
          item.executionCount(),
          item.activeMonitorCount(),
          item.executedRuleCount(),
          item.passedRuleCount(),
          item.failedRuleCount(),
          item.errorRuleCount(),
          item.issueExecutionCount(),
          rate(item.passedRuleCount(), item.executedRuleCount()),
          rate(issues, item.executedRuleCount()),
          item.averageDurationMs()));
    }
    return List.copyOf(result);
  }

  private DateRange resolveRange(LocalDate requestedStart, LocalDate requestedEnd) {
    if (requestedStart == null && requestedEnd == null) {
      LocalDate end = LocalDate.now().minusDays(1);
      return new DateRange(end.minusDays(RANGE_DAYS - 1L), end);
    }
    if (requestedStart == null || requestedEnd == null) {
      throw new IllegalArgumentException("startDate 和 endDate 必须同时提供");
    }
    if (requestedEnd.isBefore(requestedStart)) {
      throw new IllegalArgumentException("endDate 不能早于 startDate");
    }
    long days = ChronoUnit.DAYS.between(requestedStart, requestedEnd) + 1L;
    if (days > MAX_ANALYTICS_RANGE_DAYS) {
      throw new IllegalArgumentException("质量总览查询区间不能超过 90 天");
    }
    return new DateRange(requestedStart, requestedEnd);
  }

  private DimensionHealth dimension(DimensionSummary item) {
    return new DimensionHealth(
        item.dimension(),
        item.total(),
        item.issues(),
        rate(item.passed(), item.total()));
  }

  private RecentIssue issue(IssueSummary item) {
    return new RecentIssue(
        String.valueOf(item.id()),
        item.executionNo(),
        String.valueOf(item.monitorId()),
        item.monitorName(),
        item.objectName(),
        item.tableName(),
        item.ruleName(),
        item.dimension(),
        item.columnName(),
        item.checkResult(),
        item.queuedAt());
  }

  private static Double rate(long numerator, long total) {
    if (total <= 0L) return null;
    return Math.round((numerator * 10000D) / total) / 100D;
  }

  private record DateRange(LocalDate start, LocalDate end) {
  }

  public record Overview(
      LocalDate rangeStart,
      LocalDate rangeEnd,
      Double passRate,
      long monitoredTableCount,
      long enabledRuleCount,
      long todayExecutionCount,
      long todayIssueTableCount,
      long recentIssueCount,
      List<DimensionHealth> dimensions,
      List<RecentIssue> recentIssues) {

    public Overview {
      dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
      recentIssues = recentIssues == null ? List.of() : List.copyOf(recentIssues);
    }
  }

  public record AnalyticsOverview(
      LocalDate rangeStart,
      LocalDate rangeEnd,
      AnalyticsSummary summary,
      List<DimensionHealth> dimensions,
      List<IssueContributor> issueContributors,
      List<TrendPoint> trend) {

    public AnalyticsOverview {
      dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
      issueContributors = issueContributors == null ? List.of() : List.copyOf(issueContributors);
      trend = trend == null ? List.of() : List.copyOf(trend);
    }
  }

  public record AnalyticsSummary(
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
      LocalDateTime latestExecutionAt) {
  }

  public record DimensionHealth(
      String dimension,
      long total,
      long issues,
      Double passRate) {
  }

  public record IssueContributor(String dimension, long issues, Double ratio) {
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

  public record RecentIssue(
      String id,
      String executionNo,
      String monitorId,
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
