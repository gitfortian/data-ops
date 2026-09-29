package io.yak.ops.business.sync.realtime.execution.query;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobListQuery;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import org.springframework.stereotype.Component;

/** Read-only task and execution projection query behind the stable query facade. */
@Component
public class RealtimeJobReadModelQuery {

  private final RealtimeJobListQuery listQuery;
  private final RealtimeJobStore store;

  public RealtimeJobReadModelQuery(RealtimeJobListQuery listQuery, RealtimeJobStore store) {
    this.listQuery = listQuery;
    this.store = store;
  }

  public RealtimeJobView detail(long id) {
    return store.view(id);
  }

  public RealtimeJobPage page(
      int pageNo,
      int pageSize,
      String keyword,
      Long id,
      String releaseState,
      String stateGroup) {
    return listQuery.page(pageNo, pageSize, keyword, id, releaseState, stateGroup);
  }
}
