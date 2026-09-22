package io.yak.ops.business.sync.offline.dao;

import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import java.util.List;

/** 离线同步执行事件数据访问接口。 */
public interface OfflineExecutionEventDao {
  boolean insert(OfflineExecutionEventPO event);
  List<OfflineExecutionEventPO> selectByExecutionId(Long executionId);
  List<OfflineExecutionEventPO> selectAfter(Long executionId, long afterId, int limit);
}
