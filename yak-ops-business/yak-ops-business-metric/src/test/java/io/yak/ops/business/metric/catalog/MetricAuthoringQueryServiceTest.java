package io.yak.ops.business.metric.catalog;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricAuthoringQueryServiceTest {

  @Test
  void forwardsStableDomainAndProcessContextToProjectScopedRepository() {
    MetricRepository repository = mock(MetricRepository.class);
    MetricAuthoringQueryService service = new MetricAuthoringQueryService(repository);
    PageData<Metric> expected = new PageData<>(List.of(), 0L, 0L, 1L, 20L);

    when(repository.page(1, 20, 11L, 22L, "ATOMIC", "ENABLED", "gmv", "alice", List.of(3L)))
        .thenReturn(expected);

    PageData<Metric> actual = service.page(
        1, 20, 11L, 22L, "ATOMIC", "ENABLED", "gmv", "alice", List.of(3L));

    assertSame(expected, actual);
    verify(repository).page(1, 20, 11L, 22L, "ATOMIC", "ENABLED", "gmv", "alice", List.of(3L));
  }
}
