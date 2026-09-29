package io.yak.ops.business.sync.realtime.execution.query;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime task and execution read models. */
@Service("realtimeJobQueryApplicationService")
public class RealtimeJobQueryService {

  private final RealtimeJobReadModelQuery query;

  public RealtimeJobQueryService(RealtimeJobReadModelQuery query) {
    this.query = query;
  }

  public RealtimeJobView detail(long id) {
    return query.detail(id);
  }

  public RealtimeJobPage page(
      int pageNo,
      int pageSize,
      String keyword,
      Long id,
      String releaseState,
      String stateGroup) {
    return query.page(pageNo, pageSize, keyword, id, releaseState, stateGroup);
  }
}
