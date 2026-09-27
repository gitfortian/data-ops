package io.yak.ops.business.metric.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricQueryServiceTest {

  @Test
  void forwardsStableBusinessContextToProjectScopedRepository() {
    MetricRepository repository = mock(MetricRepository.class);
    MetricQueryService service = new MetricQueryService(repository);
    PageData<Metric> expected = new PageData<>(List.of(), 0, 0, 1, 20);

    when(repository.page(1, 20, 7L, 11L, null, null, null, null, List.of()))
        .thenReturn(expected);

    PageData<Metric> actual = service.page(1, 20, 7L, 11L, null, null, null, null, List.of());

    assertThat(actual).isSameAs(expected);
    verify(repository).page(1, 20, 7L, 11L, null, null, null, null, List.of());
  }
}
