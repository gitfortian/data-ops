package io.yak.ops.business.dataservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository.ApiStatistics;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository.Snapshot;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository.TrendBucket;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceOverviewReaderTest {

  @Test
  void shouldBuildOverviewFromSqlAggregatesWithoutLoadingRawLogs() {
    DataServiceOverviewRepository repository = mock(DataServiceOverviewRepository.class);
    DataServiceOverviewReader reader = new DataServiceOverviewReader(repository);
    LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 30);
    InvocationRecord failure =
        new InvocationRecord(
            88L,
            7L,
            "订单查询",
            "/orders",
            "PUBLIC",
            null,
            null,
            null,
            null,
            false,
            180L,
            0,
            "timeout",
            now.minusMinutes(5));
    Snapshot snapshot =
        new Snapshot(
            6L,
            4L,
            4L,
            3L,
            400L,
            22L,
            List.of(
                new TrendBucket(0, 1L, 1L, 0L, 80L),
                new TrendBucket(23, 3L, 2L, 1L, 320L)),
            List.of(new ApiStatistics(7L, "订单查询", "/orders", 3L, 2L, 300L)),
            List.of(failure));
    when(repository.load(any(), eq(now), eq(60), eq(24), eq(8), eq(8)))
        .thenReturn(snapshot);

    DataServiceOverviewReader.Overview overview = reader.overviewAt("24h", now);

    assertThat(overview.apiTotal()).isEqualTo(6L);
    assertThat(overview.runningApis()).isEqualTo(4L);
    assertThat(overview.stoppedApis()).isEqualTo(2L);
    assertThat(overview.totalCalls()).isEqualTo(4L);
    assertThat(overview.successRate()).isEqualTo(75D);
    assertThat(overview.averageDurationMs()).isEqualTo(100L);
    assertThat(overview.trend()).hasSize(24);
    assertThat(overview.trend().get(0).calls()).isEqualTo(1L);
    assertThat(overview.trend().get(23).failureCalls()).isEqualTo(1L);
    assertThat(overview.hotApis())
        .singleElement()
        .satisfies(
            api -> {
              assertThat(api.name()).isEqualTo("订单查询");
              assertThat(api.calls()).isEqualTo(3L);
              assertThat(api.successRate()).isEqualTo(66.7D);
              assertThat(api.averageDurationMs()).isEqualTo(100L);
            });
    assertThat(overview.recentFailures()).singleElement().isEqualTo(
        DataServiceOverviewReader.FailureItem.from(failure));
    verify(repository).load(any(), eq(now), eq(60), eq(24), eq(8), eq(8));
  }

  @Test
  void shouldRejectUnsupportedRangeBeforeQueryingRepository() {
    DataServiceOverviewRepository repository = mock(DataServiceOverviewRepository.class);
    DataServiceOverviewReader reader = new DataServiceOverviewReader(repository);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> reader.overviewAt("90d", LocalDateTime.of(2026, 8, 26, 10, 30)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
