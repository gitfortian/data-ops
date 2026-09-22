package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineBatchExecutionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的离线同步业务批次数据访问实现。 */
@ConditionalOnOfflineSyncEnabled
@Repository
public class OfflineBatchExecutionDaoImpl implements OfflineBatchExecutionDao {

  private final OfflineBatchExecutionMapper mapper;
  private final CurrentProject currentProject;

  public OfflineBatchExecutionDaoImpl(
      OfflineBatchExecutionMapper mapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public OfflineBatchExecutionPO selectById(Long id) {
    long projectId = requiredProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getId, id)
            .eq(OfflineBatchExecutionPO::getProjectId, projectId));
  }

  @Override
  public OfflineBatchExecutionPO selectByTaskIdAndBatchKey(Long taskId, String batchKey) {
    if (taskId == null || taskId <= 0L || !StringUtils.hasText(batchKey)) return null;
    long projectId = requiredProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .eq(OfflineBatchExecutionPO::getBatchKey, batchKey.trim())
            .last("LIMIT 1"));
  }

  @Override
  public boolean existsByTaskIdAndStatuses(Long taskId, List<String> statuses) {
    if (taskId == null || taskId <= 0L || statuses == null || statuses.isEmpty()) return false;
    long projectId = requiredProjectId();
    return mapper.selectCount(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .in(OfflineBatchExecutionPO::getStatus, statuses)) > 0L;
  }

  @Override
  public OfflineBatchExecutionPO selectLatestByTaskIdAndStatuses(
      Long taskId, List<String> statuses) {
    if (taskId == null || taskId <= 0L || statuses == null || statuses.isEmpty()) return null;
    long projectId = requiredProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .in(OfflineBatchExecutionPO::getStatus, statuses)
            .orderByDesc(OfflineBatchExecutionPO::getId)
            .last("LIMIT 1"));
  }

  @Override
  public List<OfflineBatchExecutionPO> selectPendingBackfills(int limit) {
    long projectId = requiredProjectId();
    return mapper.selectList(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getTriggerType, "BACKFILL")
            .eq(OfflineBatchExecutionPO::getStatus, "PENDING")
            .orderByAsc(OfflineBatchExecutionPO::getId)
            .last("LIMIT " + Math.max(1, limit)));
  }

  @Override
  public List<OfflineBatchExecutionPO> selectPendingBackfillsForDispatch(int limit) {
    return mapper.selectList(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .isNotNull(OfflineBatchExecutionPO::getProjectId)
            .eq(OfflineBatchExecutionPO::getTriggerType, "BACKFILL")
            .eq(OfflineBatchExecutionPO::getStatus, "PENDING")
            .orderByAsc(OfflineBatchExecutionPO::getId)
            .last("LIMIT " + Math.max(1, limit)));
  }

  @Override
  public boolean reservePendingBackfill(Long batchId, LocalDateTime updateTime) {
    if (batchId == null || batchId <= 0L) return false;
    long projectId = requiredProjectId();
    return mapper.update(
        null,
        Wrappers.<OfflineBatchExecutionPO>lambdaUpdate()
            .eq(OfflineBatchExecutionPO::getId, batchId)
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getTriggerType, "BACKFILL")
            .eq(OfflineBatchExecutionPO::getStatus, "PENDING")
            .set(OfflineBatchExecutionPO::getStatus, "RUNNING")
            .set(OfflineBatchExecutionPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public boolean updateAuditCarrier(
      Long batchId, String carrierJson, LocalDateTime updateTime) {
    if (batchId == null || batchId <= 0L || !StringUtils.hasText(carrierJson)) return false;
    long projectId = requiredProjectId();
    return mapper.update(
        null,
        Wrappers.<OfflineBatchExecutionPO>lambdaUpdate()
            .eq(OfflineBatchExecutionPO::getId, batchId)
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)
            .set(OfflineBatchExecutionPO::getAuditCarrierJson, carrierJson.trim())
            .set(OfflineBatchExecutionPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public boolean insert(OfflineBatchExecutionPO batchPO) {
    bindCurrentProject(batchPO);
    return mapper.insert(batchPO) > 0;
  }

  @Override
  public boolean updateById(OfflineBatchExecutionPO batchPO) {
    long projectId = requiredProjectId();
    bindCurrentProject(batchPO);
    return mapper.update(
        batchPO,
        Wrappers.<OfflineBatchExecutionPO>lambdaUpdate()
            .eq(OfflineBatchExecutionPO::getId, batchPO.getId())
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)) > 0;
  }

  private long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private void bindCurrentProject(OfflineBatchExecutionPO batchPO) {
    long projectId = requiredProjectId();
    if (batchPO.getProjectId() != null && !Objects.equals(projectId, batchPO.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    batchPO.setProjectId(projectId);
  }
}
