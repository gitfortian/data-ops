package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineExecutionEventMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的执行事件数据访问实现。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineExecutionEventDaoImpl implements OfflineExecutionEventDao {
  private final OfflineExecutionEventMapper mapper;

  @Override
  public boolean insert(OfflineExecutionEventPO event) {
    return mapper.insert(event) > 0;
  }

  @Override
  public List<OfflineExecutionEventPO> selectByExecutionId(Long executionId) {
    return mapper.selectList(
        Wrappers.<OfflineExecutionEventPO>lambdaQuery()
            .eq(OfflineExecutionEventPO::getExecutionId, executionId)
            .orderByAsc(OfflineExecutionEventPO::getId));
  }

  @Override
  public List<OfflineExecutionEventPO> selectAfter(Long executionId, long afterId, int limit) {
    return mapper.selectList(
        Wrappers.<OfflineExecutionEventPO>lambdaQuery()
            .eq(OfflineExecutionEventPO::getExecutionId, executionId)
            .gt(OfflineExecutionEventPO::getId, afterId)
            .orderByAsc(OfflineExecutionEventPO::getId)
            .last("LIMIT " + Math.max(1, limit)));
  }
}
