package io.yak.ops.business.sync.offline.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobExecutionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Project-explicit adapter for resolving execution -> definition notification policy. */
@Repository
@ConditionalOnOfflineSyncEnabled
@RequiredArgsConstructor
public class OfflineNotificationPolicyReaderAdapter implements OfflineNotificationPolicyReader {

  private final OfflineJobExecutionMapper executionMapper;
  private final OfflineJobDefinitionMapper definitionMapper;

  @Override
  public Optional<Snapshot> find(long projectId, long executionId) {
    OfflineJobExecutionPO execution = executionMapper.selectOne(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getId, executionId)
            .eq(OfflineJobExecutionPO::getProjectId, projectId)
            .last("LIMIT 1"));
    if (execution == null || execution.getJobDefinitionId() == null) return Optional.empty();

    OfflineJobDefinitionPO definition = definitionMapper.selectOne(
        Wrappers.<OfflineJobDefinitionPO>lambdaQuery()
            .eq(OfflineJobDefinitionPO::getId, execution.getJobDefinitionId())
            .eq(OfflineJobDefinitionPO::getProjectId, projectId)
            .last("LIMIT 1"));
    if (definition == null) return Optional.empty();

    return Optional.of(new Snapshot(
        definition.getId(), definition.getNotificationConfigJson()));
  }
}
