package io.yak.ops.business.home.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.workspace.QualityOverviewReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HomeQualityOverviewReaderTest {

  @Test
  void shouldExposeRealQualityOverviewValues() {
    QualityOverviewReader qualityReader = mock(QualityOverviewReader.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<QualityOverviewReader> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(qualityReader);

    LocalDate today = LocalDate.now();
    LocalDateTime issueTime = today.atTime(10, 30);
    when(qualityReader.overview())
        .thenReturn(new QualityOverviewReader.Overview(
            today.minusDays(6),
            today,
            97.5D,
            8L,
            24L,
            6L,
            1L,
            2L,
            List.of(new QualityOverviewReader.DimensionHealth("完整性", 12L, 1L, 91.67D)),
            List.of(new QualityOverviewReader.RecentIssue(
                "91",
                "quality-1",
                "11",
                "订单质量监控",
                "warehouse.dwd_order",
                "dwd_order",
                "订单号不能为空",
                "完整性",
                "order_id",
                "NOT_PASSED",
                issueTime))));

    HomeQualityOverviewReader.OverviewResponse response =
        new HomeQualityOverviewReader(provider).overview();

    assertThat(response.available()).isTrue();
    assertThat(response.passRate()).isEqualTo(97.5D);
    assertThat(response.monitoredTableCount()).isEqualTo(8L);
    assertThat(response.enabledRuleCount()).isEqualTo(24L);
    assertThat(response.todayExecutionCount()).isEqualTo(6L);
    assertThat(response.todayIssueTableCount()).isEqualTo(1L);
    assertThat(response.recentIssueCount()).isEqualTo(2L);
    assertThat(response.dimensions()).singleElement()
        .satisfies(item -> {
          assertThat(item.dimension()).isEqualTo("完整性");
          assertThat(item.passRate()).isEqualTo(91.67D);
        });
    assertThat(response.recentIssues()).singleElement()
        .satisfies(item -> {
          assertThat(item.executionNo()).isEqualTo("quality-1");
          assertThat(item.ruleName()).isEqualTo("订单号不能为空");
          assertThat(item.queuedAt()).isEqualTo(issueTime);
        });
  }

  @Test
  void shouldExposeUnavailableStateWithoutInventingZeroes() {
    @SuppressWarnings("unchecked")
    ObjectProvider<QualityOverviewReader> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);

    HomeQualityOverviewReader.OverviewResponse response =
        new HomeQualityOverviewReader(provider).overview();

    assertThat(response.available()).isFalse();
    assertThat(response.passRate()).isNull();
    assertThat(response.monitoredTableCount()).isNull();
    assertThat(response.enabledRuleCount()).isNull();
    assertThat(response.todayExecutionCount()).isNull();
    assertThat(response.todayIssueTableCount()).isNull();
    assertThat(response.recentIssueCount()).isNull();
    assertThat(response.dimensions()).isEmpty();
    assertThat(response.recentIssues()).isEmpty();
  }

  @Test
  void shouldKeepHomepageAvailableWhenQualityQueryFails() {
    QualityOverviewReader qualityReader = mock(QualityOverviewReader.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<QualityOverviewReader> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(qualityReader);
    when(qualityReader.overview()).thenThrow(new IllegalStateException("quality unavailable"));

    HomeQualityOverviewReader.OverviewResponse response =
        new HomeQualityOverviewReader(provider).overview();

    assertThat(response.available()).isFalse();
    assertThat(response.passRate()).isNull();
    assertThat(response.monitoredTableCount()).isNull();
    assertThat(response.recentIssues()).isEmpty();
  }
}
