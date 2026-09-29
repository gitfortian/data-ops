package io.yak.ops.business.sync.realtime.execution.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.yak.ops.business.sync.realtime.repository.RealtimeJobListQuery;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import org.junit.jupiter.api.Test;

class RealtimeJobReadModelQueryTest {

  @Test
  void detailAndPageReadOnlyThroughRepositoryQueries() {
    RealtimeJobListQuery listQuery = mock(RealtimeJobListQuery.class);
    RealtimeJobStore store = mock(RealtimeJobStore.class);
    RealtimeJobReadModelQuery query = new RealtimeJobReadModelQuery(listQuery, store);

    assertThat(query.detail(7L)).isNull();
    assertThat(query.page(1, 20, "orders", 7L, "PUBLISHED", "ACTIVE")).isNull();

    verify(store).view(7L);
    verify(listQuery).page(1, 20, "orders", 7L, "PUBLISHED", "ACTIVE");
  }
}
