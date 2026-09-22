package io.yak.ops.business.dashboard.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardOverview;
import io.yak.ops.business.dashboard.repository.DashboardOverviewRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardOverviewReaderTest {

  @Test
  void shouldUseBoundedDashboardProjection() {
    DashboardOverviewRepository repository = mock(DashboardOverviewRepository.class);
    DashboardOverviewReader reader = new DashboardOverviewReader(repository);
    DashboardAsset recent =
        new DashboardAsset(
            9L,
            "销售经营分析",
            null,
            91L,
            3,
            90L,
            2,
            Instant.parse("2026-08-25T08:00:00Z"),
            Instant.parse("2026-08-20T08:00:00Z"),
            Instant.parse("2026-08-26T08:00:00Z"));
    when(repository.summarize())
        .thenReturn(new DashboardOverviewRepository.Summary(12L, 8L));
    when(repository.listRecent(4)).thenReturn(List.of(recent));

    DashboardOverview overview = reader.overview(4);

    assertThat(overview.dashboardCount()).isEqualTo(12L);
    assertThat(overview.publishedDashboardCount()).isEqualTo(8L);
    assertThat(overview.recentDashboards()).containsExactly(recent);
    verify(repository).listRecent(4);
  }

  @Test
  void shouldClampListLimit() {
    DashboardOverviewRepository repository = mock(DashboardOverviewRepository.class);
    DashboardOverviewReader reader = new DashboardOverviewReader(repository);
    when(repository.summarize())
        .thenReturn(new DashboardOverviewRepository.Summary(0L, 0L));

    reader.overview(100);

    verify(repository).listRecent(20);
  }
}
