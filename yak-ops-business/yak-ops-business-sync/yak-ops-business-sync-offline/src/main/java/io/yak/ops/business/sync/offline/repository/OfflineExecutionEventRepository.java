package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import java.util.List;

/** 离线同步执行事件领域仓储。 */
public interface OfflineExecutionEventRepository {
  void append(OfflineExecutionEvent event);
  List<OfflineExecutionEvent> list(Long executionId);
  List<OfflineExecutionEvent> listAfter(Long executionId, long afterId, int limit);
}
