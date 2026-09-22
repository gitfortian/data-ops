package io.yak.ops.business.quality.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityExecutionDao;
import io.yak.ops.business.quality.dao.mapper.QualityExecutionMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.mapper.QualityRuleExecutionMapper;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.RuleExecutionWorkspaceRow;
import io.yak.ops.common.bean.po.quality.QualityRuleExecutionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityExecutionDaoImpl implements QualityExecutionDao {
  private final QualityExecutionMapper executionMapper;
  private final QualityRuleExecutionMapper ruleExecutionMapper;
  private final QualityQueryMapper queryMapper;
  private final CurrentProject currentProject;

  @Override
  public boolean hasActive(long monitorId) {
    long projectId = currentProjectId();
    return executionMapper.selectCount(
            Wrappers.<QualityExecutionPO>lambdaQuery()
                .eq(QualityExecutionPO::getProjectId, projectId)
                .eq(QualityExecutionPO::getMonitorId, monitorId)
                .in(
                    QualityExecutionPO::getExecutionStatus,
                    List.of("WAITING", "RUNNING")))
        > 0;
  }

  @Override
  public long insertExecution(QualityExecutionPO execution) {
    long projectId = currentProjectId();
    bindProject(execution, projectId);
    executionMapper.insert(execution);
    if (execution.getId() == null) {
      throw new IllegalStateException("质量检查已创建，但未返回执行编号");
    }
    return execution.getId();
  }

  @Override
  public boolean markRunning(long id, LocalDateTime startedAt) {
    long projectId = currentProjectId();
    return executionMapper.update(
            null,
            Wrappers.<QualityExecutionPO>lambdaUpdate()
                .eq(QualityExecutionPO::getProjectId, projectId)
                .eq(QualityExecutionPO::getId, id)
                .eq(QualityExecutionPO::getExecutionStatus, "WAITING")
                .set(QualityExecutionPO::getExecutionStatus, "RUNNING")
                .set(QualityExecutionPO::getCheckResult, "RUNNING")
                .set(QualityExecutionPO::getStartedAt, startedAt)
                .set(QualityExecutionPO::getErrorMessage, null))
        > 0;
  }

  @Override
  public void insertRuleExecution(QualityRuleExecutionPO ruleExecution) {
    requireOwnedExecution(ruleExecution.getExecutionId());
    ruleExecutionMapper.insert(ruleExecution);
  }

  @Override
  public boolean complete(
      long id,
      String result,
      int passed,
      int failed,
      int errors,
      LocalDateTime finishedAt,
      long durationMs) {
    long projectId = currentProjectId();
    return executionMapper.update(
            null,
            Wrappers.<QualityExecutionPO>lambdaUpdate()
                .eq(QualityExecutionPO::getProjectId, projectId)
                .eq(QualityExecutionPO::getId, id)
                .eq(QualityExecutionPO::getExecutionStatus, "RUNNING")
                .set(QualityExecutionPO::getExecutionStatus, "SUCCESS")
                .set(QualityExecutionPO::getCheckResult, result)
                .set(QualityExecutionPO::getPassedRules, passed)
                .set(QualityExecutionPO::getFailedRules, failed)
                .set(QualityExecutionPO::getErrorRules, errors)
                .set(QualityExecutionPO::getFinishedAt, finishedAt)
                .set(QualityExecutionPO::getDurationMs, durationMs)
                .set(QualityExecutionPO::getErrorMessage, null))
        > 0;
  }

  @Override
  public boolean fail(
      long id,
      String errorMessage,
      LocalDateTime finishedAt,
      long durationMs) {
    long projectId = currentProjectId();
    return executionMapper.update(
            null,
            Wrappers.<QualityExecutionPO>lambdaUpdate()
                .eq(QualityExecutionPO::getProjectId, projectId)
                .eq(QualityExecutionPO::getId, id)
                .in(
                    QualityExecutionPO::getExecutionStatus,
                    List.of("WAITING", "RUNNING"))
                .set(QualityExecutionPO::getExecutionStatus, "FAILED")
                .set(QualityExecutionPO::getCheckResult, "ERROR")
                .set(QualityExecutionPO::getErrorMessage, errorMessage)
                .set(QualityExecutionPO::getFinishedAt, finishedAt)
                .set(QualityExecutionPO::getDurationMs, durationMs))
        > 0;
  }

  @Override
  public long countExecutions(Map<String, Object> params) {
    return queryMapper.countExecutions(scoped(params));
  }

  @Override
  public List<QualityExecutionPO> selectExecutions(Map<String, Object> params) {
    return queryMapper.selectExecutions(scoped(params));
  }

  @Override
  public QualityExecutionPO selectByExecutionNo(String executionNo) {
    long projectId = currentProjectId();
    return executionMapper.selectOne(
        Wrappers.<QualityExecutionPO>lambdaQuery()
            .eq(QualityExecutionPO::getProjectId, projectId)
            .eq(QualityExecutionPO::getExecutionNo, executionNo));
  }

  @Override
  public List<QualityRuleExecutionPO> selectRuleExecutions(long executionId) {
    requireOwnedExecution(executionId);
    return ruleExecutionMapper.selectList(
        Wrappers.<QualityRuleExecutionPO>lambdaQuery()
            .eq(QualityRuleExecutionPO::getExecutionId, executionId)
            .orderByAsc(QualityRuleExecutionPO::getId));
  }

  @Override
  public long countExecutionWorkspace(Map<String, Object> params) {
    return queryMapper.countExecutionWorkspace(scoped(params));
  }

  @Override
  public List<QualityExecutionPO> selectExecutionWorkspace(Map<String, Object> params) {
    return queryMapper.selectExecutionWorkspace(scoped(params));
  }

  @Override
  public long countRuleExecutionWorkspace(Map<String, Object> params) {
    return queryMapper.countRuleExecutionWorkspace(scoped(params));
  }

  @Override
  public List<RuleExecutionWorkspaceRow> selectRuleExecutionWorkspace(
      Map<String, Object> params) {
    return queryMapper.selectRuleExecutionWorkspace(scoped(params));
  }

  private Map<String, Object> scoped(Map<String, Object> params) {
    Map<String, Object> scoped = new LinkedHashMap<>();
    if (params != null) scoped.putAll(params);
    scoped.put("projectId", currentProjectId());
    return scoped;
  }

  private void requireOwnedExecution(Long executionId) {
    if (executionId == null || executionId <= 0L) throw projectNotFound();
    long projectId = currentProjectId();
    Long count =
        executionMapper.selectCount(
            Wrappers.<QualityExecutionPO>lambdaQuery()
                .eq(QualityExecutionPO::getProjectId, projectId)
                .eq(QualityExecutionPO::getId, executionId));
    if (count == null || count == 0L) throw projectNotFound();
  }

  private void bindProject(QualityExecutionPO execution, long projectId) {
    if (execution.getProjectId() != null
        && !Objects.equals(execution.getProjectId(), projectId)) {
      throw projectNotFound();
    }
    execution.setProjectId(projectId);
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private static ProjectContextException projectNotFound() {
    return new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
  }
}
