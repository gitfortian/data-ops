package io.yak.ops.business.dataset.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetOverviewSnapshot;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.repository.DatasetOverviewRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetOverviewReaderTest {

  @Test
  void shouldUseBoundedRepositoryProjections() {
    DatasetOverviewRepository repository = mock(DatasetOverviewRepository.class);
    DatasetOverviewReader reader = new DatasetOverviewReader(repository);
    Instant from = Instant.parse("2026-08-26T00:00:00Z");
    Instant to = from.plusSeconds(86_400);
    Dataset recent =
        new Dataset(2L, "订单数据集", null, DatasetStatus.ONLINE, 20L, from, from.plusSeconds(60));

    when(repository.summarize(from, to))
        .thenReturn(new DatasetOverviewRepository.Summary(12L, 2L));
    when(repository.listRecent(5)).thenReturn(List.of(recent));
    when(repository.listRecentOnline(5)).thenReturn(List.of(recent));

    DatasetOverviewSnapshot overview = reader.overview(from, to, 5);

    assertThat(overview.datasetCount()).isEqualTo(12L);
    assertThat(overview.createdCount()).isEqualTo(2L);
    assertThat(overview.recentDatasets()).containsExactly(recent);
    assertThat(overview.onlineDatasets()).containsExactly(recent);
    verify(repository).listRecent(5);
    verify(repository).listRecentOnline(5);
  }

  @Test
  void shouldClampListLimit() {
    DatasetOverviewRepository repository = mock(DatasetOverviewRepository.class);
    DatasetOverviewReader reader = new DatasetOverviewReader(repository);
    Instant from = Instant.parse("2026-08-26T00:00:00Z");
    Instant to = from.plusSeconds(60);
    when(repository.summarize(from, to))
        .thenReturn(new DatasetOverviewRepository.Summary(0L, 0L));

    reader.overview(from, to, 200);

    verify(repository).listRecent(20);
    verify(repository).listRecentOnline(20);
  }
}
