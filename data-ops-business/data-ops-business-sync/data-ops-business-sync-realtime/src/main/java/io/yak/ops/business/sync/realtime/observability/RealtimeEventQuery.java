package io.yak.ops.business.sync.realtime.observability;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** Read-only query for persisted realtime execution events. */
@Component
public class RealtimeEventQuery {

  private final RealtimeJobStore store;

  public RealtimeEventQuery(RealtimeJobStore store) {
    this.store = store;
  }

  public List<RealtimeJobEventView> events(long taskId) {
    store.definition(taskId)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + taskId));
    return store.events(taskId);
  }
}
