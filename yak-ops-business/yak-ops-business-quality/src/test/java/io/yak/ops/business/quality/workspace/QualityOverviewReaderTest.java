package io.yak.ops.business.quality.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.quality.repository.QualityOverviewRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityOverviewReaderTest {

  @Test
  void shouldBuildOverviewFromPersistedQualityEvidence() {
    LocalDateTime issueTime = LocalDateTime.now().minusHours(1);
    QualityOverviewRepository repository = new StubRepository(
        new QualityOverviewRepository.OverviewStats(4L, 12L, 8L, 2L, 10L, 8L, 2L),
        List.of(
            new QualityOverviewRepository.DimensionSummary("完整性", 4L, 3L, 1L),
            new QualityOverviewRepository.DimensionSummary("唯一性", 6L, 5L, 1L)),
        List.of(
            new QualityOverviewRepository.IssueSummary(
                91L,
                "quality-20260826-1",
                11L,
                "订单完整性监控",
                "warehouse.dwd_order",
                "dwd_order",
                "订单号不能为空",
                "完整性",
                "order_id",
                "NOT_PASSED",
                issueTime)));

    QualityOverviewReader.Overview overview = new QualityOverviewReader(repository).overview();

    assertThat(overview.rangeStart()).isEqualTo(LocalDate.now().minusDays(6));
    assertThat(overview.rangeEnd()).isEqualTo(LocalDate.now());
    assertThat(overview.passRate()).isEqualTo(80D);
    assertThat(overview.monitoredTableCount()).isEqualTo(4L);
    assertThat(overview.enabledRuleCount()).isEqualTo(12L);
    assertThat(overview.todayExecutionCount()).isEqualTo(8L);
    assertThat(overview.todayIssueTableCount()).isEqualTo(2L);
    assertThat(overview.recentIssueCount()).isEqualTo(2L);
    assertThat(overview.dimensions())
        .extracting(QualityOverviewReader.DimensionHealth::passRate)
        .containsExactly(75D, 83.33D);
    assertThat(overview.recentIssues())
        .singleElement()
        .satisfies(issue -> {
          assertThat(issue.executionNo()).isEqualTo("quality-20260826-1");
          assertThat(issue.ruleName()).isEqualTo("订单号不能为空");
          assertThat(issue.dimension()).isEqualTo("完整性");
          assertThat(issue.checkResult()).isEqualTo("NOT_PASSED");
          assertThat(issue.queuedAt()).isEqualTo(issueTime);
        });
  }

  @Test
  void shouldExposeFourRecentIssuesForHomepage() {
    QualityOverviewRepository repository = new StubRepository(
        new QualityOverviewRepository.OverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 5L),
        List.of(),
        List.of(issue(1L), issue(2L), issue(3L), issue(4L), issue(5L)));

    QualityOverviewReader.Overview overview = new QualityOverviewReader(repository).overview();

    assertThat(overview.recentIssues()).hasSize(4);
  }

  @Test
  void shouldKeepPassRateUnavailableWhenNoRulesWereExecuted() {
    QualityOverviewRepository repository = new StubRepository(
        new QualityOverviewRepository.OverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0L),
        List.of(),
        List.of());

    QualityOverviewReader.Overview overview = new QualityOverviewReader(repository).overview();

    assertThat(overview.passRate()).isNull();
    assertThat(overview.monitoredTableCount()).isZero();
    assertThat(overview.todayExecutionCount()).isZero();
    assertThat(overview.dimensions()).isEmpty();
    assertThat(overview.recentIssues()).isEmpty();
  }

  @Test
  void shouldBuildRangeAnalyticsAndFillMissingDates() {
    LocalDate start = LocalDate.of(2026, 8, 20);
    LocalDate end = LocalDate.of(2026, 8, 22);
    QualityOverviewRepository repository = new StubRepository(
        new QualityOverviewRepository.OverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0L),
        List.of(
            new QualityOverviewRepository.DimensionSummary("完整性", 10L, 8L, 2L),
            new QualityOverviewRepository.DimensionSummary("唯一性", 4L, 4L, 0L)),
        List.of(),
        new QualityOverviewRepository.AnalyticsStats(
            5L, 3L, 14L, 12L, 1L, 1L, 2L, 2L, 2L, 1L,
            250.5D, LocalDateTime.of(2026, 8, 22, 9, 30)),
        List.of(
            new QualityOverviewRepository.TrendSummary(
                LocalDate.of(2026, 8, 20), 2L, 2L, 6L, 5L, 1L, 0L, 1L, 200D),
            new QualityOverviewRepository.TrendSummary(
                LocalDate.of(2026, 8, 22), 3L, 2L, 8L, 7L, 0L, 1L, 1L, 300D)));

    QualityOverviewReader.AnalyticsOverview overview =
        new QualityOverviewReader(repository).analytics(start, end);

    assertThat(overview.rangeStart()).isEqualTo(start);
    assertThat(overview.rangeEnd()).isEqualTo(end);
    assertThat(overview.summary().passRate()).isEqualTo(85.71D);
    assertThat(overview.summary().issueRate()).isEqualTo(14.29D);
    assertThat(overview.issueContributors())
        .singleElement()
        .satisfies(item -> {
          assertThat(item.dimension()).isEqualTo("完整性");
          assertThat(item.issues()).isEqualTo(2L);
          assertThat(item.ratio()).isEqualTo(100D);
        });
    assertThat(overview.trend()).hasSize(3);
    assertThat(overview.trend().get(1).date()).isEqualTo(LocalDate.of(2026, 8, 21));
    assertThat(overview.trend().get(1).executionCount()).isZero();
    assertThat(overview.trend().get(2).errorRuleCount()).isEqualTo(1L);
  }

  @Test
  void shouldRejectOversizedAnalyticsRange() {
    QualityOverviewReader reader = new QualityOverviewReader(new StubRepository(
        new QualityOverviewRepository.OverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0L),
        List.of(),
        List.of()));

    assertThatThrownBy(() -> reader.analytics(
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 15)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("90 天");
  }

  private static QualityOverviewRepository.IssueSummary issue(long id) {
    return new QualityOverviewRepository.IssueSummary(
        id,
        "quality-" + id,
        11L,
        "订单质量监控",
        "warehouse.dwd_order",
        "dwd_order",
        "订单号不能为空",
        "完整性",
        "order_id",
        "NOT_PASSED",
        LocalDateTime.now().minusMinutes(id));
  }

  private record StubRepository(
      OverviewStats stats,
      List<DimensionSummary> dimensions,
      List<IssueSummary> recentIssues,
      AnalyticsStats analyticsStats,
      List<TrendSummary> trend) implements QualityOverviewRepository {

    StubRepository(
        OverviewStats stats,
        List<DimensionSummary> dimensions,
        List<IssueSummary> recentIssues) {
      this(stats, dimensions, recentIssues, emptyAnalytics(), List.of());
    }

    @Override
    public OverviewStats stats(
        LocalDateTime todayStart,
        LocalDateTime todayEnd,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd) {
      return stats;
    }

    @Override
    public AnalyticsStats analyticsStats(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
      return analyticsStats;
    }

    @Override
    public List<DimensionSummary> dimensions(
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd) {
      return dimensions;
    }

    @Override
    public List<TrendSummary> trend(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
      return trend;
    }

    @Override
    public List<IssueSummary> recentIssues(
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        int limit) {
      return recentIssues.stream().limit(limit).toList();
    }

    private static AnalyticsStats emptyAnalytics() {
      return new AnalyticsStats(
          0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null);
    }
  }
}
