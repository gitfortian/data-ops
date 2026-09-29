package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;

/** Read repository contract for the realtime synchronization list page. */
public interface RealtimeJobListQuery {

  RealtimeJobPage page(
      int pageNo,
      int pageSize,
      String keyword,
      Long id,
      String releaseState,
      String stateGroup);
}
