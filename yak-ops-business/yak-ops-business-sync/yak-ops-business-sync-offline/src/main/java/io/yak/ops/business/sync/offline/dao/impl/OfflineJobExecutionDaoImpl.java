package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobExecutionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的 ExecutionAttempt 持久化 DAO。 */
@ConditionalOnOfflineSyncEnabled
@Repository
public class OfflineJobExecutionDaoImpl implements OfflineJobExecutionDao {

  private static final List<String> ACTIVE_STATUSES =
      List.of("CREATED", "SUBMITTED", "QUEUED", "RUNNING", "UNKNOWN");

  private final OfflineJobExecutionMapper mapper;
  private final CurrentProject currentProject;

  public OfflineJobExecutionDaoImpl(
      OfflineJobExecutionMapper mapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public OfflineJobExecutionPO selectById(Long id) {
    long projectId = requiredProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getId, id)
            .eq(OfflineJobExecutionPO::getProjectId, projectId));
  }

  @Override
  public OfflineJobExecutionPO selectByIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) return null;
    long projectId = requiredProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getProjectId, projectId)
            .eq(OfflineJobExecutionPO::getIdempotencyKey, idempotencyKey.trim())
            .last("LIMIT 1"));
  }

  @Override
  public List<OfflineJobExecutionPO> selectByBatchId(Long batchId) {
    if (batchId == null || batchId <= 0L) return List.of();
    long projectId = requiredProjectId();
    return mapper.selectList(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getProjectId, projectId)
            .eq(OfflineJobExecutionPO::getBatchId, batchId)
            .orderByAsc(OfflineJobExecutionPO::getAttemptNo)
            .orderByAsc(OfflineJobExecutionPO::getId));
  }

  @Override
  public boolean insert(OfflineJobExecutionPO executionPO) {
    bindCurrentProject(executionPO);
    return mapper.insert(executionPO) > 0;
  }

  @Override
  public boolean updateById(OfflineJobExecutionPO executionPO) {
    long projectId = requiredProjectId();
    bindCurrentProject(executionPO);
    return mapper.update(
        executionPO,
        Wrappers.<OfflineJobExecutionPO>lambdaUpdate()
            .eq(OfflineJobExecutionPO::getId, executionPO.getId())
            .eq(OfflineJobExecutionPO::getProjectId, projectId)) > 0;
  }

  @Override
  public List<OfflineJobExecutionPO> selectActiveExecutions(int limit) {
    return activeQuery(requiredProjectId(), limit, false);
  }

  @Override
  public List<OfflineJobExecutionPO> selectActiveExecutionsForReconciliation(int limit) {
    return activeQuery(null, limit, true);
  }

  @Override
  public List<OfflineJobExecutionPO> selectRetryCandidates(LocalDateTime now, int limit) {
    return retryQuery(requiredProjectId(), now, limit, false);
  }

  @Override
  public List<OfflineJobExecutionPO> selectRetryCandidatesForReconciliation(
      LocalDateTime now, int limit) {
    return retryQuery(null, now, limit, true);
  }

  @Override
  public boolean reserveRetry(Long executionId, LocalDateTime updateTime) {
    if (executionId == null || executionId <= 0L) return false;
    long projectId = requiredProjectId();
    return mapper.update(
        null,
        Wrappers.<OfflineJobExecutionPO>lambdaUpdate()
            .eq(OfflineJobExecutionPO::getId, executionId)
            .eq(OfflineJobExecutionPO::getProjectId, projectId)
            .eq(OfflineJobExecutionPO::getStatus, "FAILED")
            .eq(OfflineJobExecutionPO::getRetryCreated, false)
            .isNotNull(OfflineJobExecutionPO::getBatchId)
            .set(OfflineJobExecutionPO::getRetryCreated, true)
            .set(OfflineJobExecutionPO::getNextRetryTime, null)
            .set(OfflineJobExecutionPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public IPage<OfflineJobExecutionPO> selectPage(PageQuery query) {
    PageQuery condition = query == null ? new PageQuery(1, 10, null, null) : query;
    long projectId = requiredProjectId();
    LambdaQueryWrapper<OfflineJobExecutionPO> wrapper =
        new LambdaQueryWrapper<OfflineJobExecutionPO>()
            .eq(OfflineJobExecutionPO::getProjectId, projectId);
    if (condition.jobDefinitionId() != null && condition.jobDefinitionId() > 0L) {
      wrapper.eq(OfflineJobExecutionPO::getJobDefinitionId, condition.jobDefinitionId());
    }
    if (StringUtils.hasText(condition.status())) {
      wrapper.eq(
          OfflineJobExecutionPO::getStatus,
          condition.status().trim().toUpperCase(Locale.ROOT));
    }
    wrapper.orderByDesc(OfflineJobExecutionPO::getId);
    return mapper.selectPage(
        new Page<>(Math.max(1, condition.current()), Math.max(1, condition.pageSize())), wrapper);
  }

  private List<OfflineJobExecutionPO> activeQuery(
      Long projectId, int limit, boolean crossProjectDispatcher) {
    LambdaQueryWrapper<OfflineJobExecutionPO> query =
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(projectId != null, OfflineJobExecutionPO::getProjectId, projectId)
            .isNotNull(crossProjectDispatcher, OfflineJobExecutionPO::getProjectId)
            .isNotNull(OfflineJobExecutionPO::getBatchId)
            .in(OfflineJobExecutionPO::getStatus, ACTIVE_STATUSES)
            .orderByAsc(OfflineJobExecutionPO::getId)
            .last("LIMIT " + Math.max(1, limit));
    return mapper.selectList(query);
  }

  private List<OfflineJobExecutionPO> retryQuery(
      Long projectId, LocalDateTime now, int limit, boolean crossProjectDispatcher) {
    return mapper.selectList(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(projectId != null, OfflineJobExecutionPO::getProjectId, projectId)
            .isNotNull(crossProjectDispatcher, OfflineJobExecutionPO::getProjectId)
            .eq(OfflineJobExecutionPO::getStatus, "FAILED")
            .eq(OfflineJobExecutionPO::getRetryCreated, false)
            .isNotNull(OfflineJobExecutionPO::getBatchId)
            .isNotNull(OfflineJobExecutionPO::getNextRetryTime)
            .le(OfflineJobExecutionPO::getNextRetryTime, now)
            .orderByAsc(OfflineJobExecutionPO::getNextRetryTime)
            .last("LIMIT " + Math.max(1, limit)));
  }

  private long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private void bindCurrentProject(OfflineJobExecutionPO executionPO) {
    long projectId = requiredProjectId();
    if (executionPO.getProjectId() != null
        && !Objects.equals(projectId, executionPO.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    executionPO.setProjectId(projectId);
  }
}
