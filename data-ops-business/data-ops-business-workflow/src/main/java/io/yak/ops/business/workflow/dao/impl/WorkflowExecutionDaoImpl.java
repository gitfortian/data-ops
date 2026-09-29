package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeAttemptMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowVersionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的工作流执行 DAO；普通运行时访问全部 fail-closed。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowExecutionDaoImpl implements WorkflowExecutionDao {

  private final WorkflowExecutionMapper executionMapper;
  private final WorkflowNodeExecutionMapper nodeExecutionMapper;
  private final WorkflowNodeAttemptMapper nodeAttemptMapper;
  private final WorkflowDefinitionMapper definitionMapper;
  private final WorkflowVersionMapper versionMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowExecutionDaoImpl(
      WorkflowExecutionMapper executionMapper,
      WorkflowNodeExecutionMapper nodeExecutionMapper,
      WorkflowNodeAttemptMapper nodeAttemptMapper,
      WorkflowDefinitionMapper definitionMapper,
      WorkflowVersionMapper versionMapper,
      CurrentProject currentProject) {
    this.executionMapper = executionMapper;
    this.nodeExecutionMapper = nodeExecutionMapper;
    this.nodeAttemptMapper = nodeAttemptMapper;
    this.definitionMapper = definitionMapper;
    this.versionMapper = versionMapper;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor. Calls still fail closed without CurrentProject. */
  public WorkflowExecutionDaoImpl(
      WorkflowExecutionMapper executionMapper,
      WorkflowNodeExecutionMapper nodeExecutionMapper,
      WorkflowNodeAttemptMapper nodeAttemptMapper) {
    this(
        executionMapper,
        nodeExecutionMapper,
        nodeAttemptMapper,
        null,
        null,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public WorkflowExecutionPO selectExecution(String executionId) {
    long projectId = currentProjectId();
    return executionMapper.selectOne(
        Wrappers.<WorkflowExecutionPO>lambdaQuery()
            .eq(WorkflowExecutionPO::getId, executionId)
            .eq(WorkflowExecutionPO::getProjectId, projectId));
  }

  @Override
  public List<WorkflowNodeExecutionPO> selectNodeExecutions(String executionId) {
    requireExecution(executionId);
    return nodeExecutionMapper.selectList(
        Wrappers.<WorkflowNodeExecutionPO>lambdaQuery()
            .eq(WorkflowNodeExecutionPO::getWorkflowExecutionId, executionId)
            .orderByAsc(WorkflowNodeExecutionPO::getId));
  }

  @Override
  public List<WorkflowNodeAttemptPO> selectNodeAttempts(String executionId) {
    requireExecution(executionId);
    return nodeAttemptMapper.selectList(
        Wrappers.<WorkflowNodeAttemptPO>lambdaQuery()
            .eq(WorkflowNodeAttemptPO::getWorkflowExecutionId, executionId)
            .orderByAsc(WorkflowNodeAttemptPO::getNodeExecutionId)
            .orderByAsc(WorkflowNodeAttemptPO::getAttemptNo));
  }

  @Override
  public int upsertExecution(WorkflowExecutionPO execution) {
    long projectId = currentProjectId();
    WorkflowExecutionPO existing = executionMapper.selectById(execution.getId());
    if (existing != null
        && (existing.getProjectId() == null || !Objects.equals(existing.getProjectId(), projectId))) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    if (execution.getProjectId() != null && !Objects.equals(execution.getProjectId(), projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    if (execution.getDefinitionId() == null
        || execution.getDefinitionId().isBlank()
        || versionMapper == null
        || versionMapper.selectByIdAndProject(execution.getDefinitionId(), projectId) == null) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    if (execution.getSourceExecutionId() != null
        && !execution.getSourceExecutionId().isBlank()
        && selectExecution(execution.getSourceExecutionId()) == null) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    execution.setProjectId(projectId);
    return executionMapper.upsert(execution);
  }

  @Override
  public int upsertNodeExecution(WorkflowNodeExecutionPO nodeExecution) {
    requireExecution(nodeExecution.getWorkflowExecutionId());
    return nodeExecutionMapper.upsert(nodeExecution);
  }

  @Override
  public int upsertNodeAttempt(WorkflowNodeAttemptPO attempt) {
    requireExecution(attempt.getWorkflowExecutionId());
    return nodeAttemptMapper.upsert(attempt);
  }

  @Override
  public int updateExecution(WorkflowExecutionPO execution) {
    long projectId = currentProjectId();
    if (execution.getProjectId() != null && !Objects.equals(execution.getProjectId(), projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    execution.setProjectId(projectId);
    return executionMapper.update(
        execution,
        Wrappers.<WorkflowExecutionPO>lambdaUpdate()
            .eq(WorkflowExecutionPO::getId, execution.getId())
            .eq(WorkflowExecutionPO::getProjectId, projectId));
  }

  @Override
  public List<String> selectExecutionIds() {
    return executionMapper.selectExecutionIds(currentProjectId());
  }

  @Override
  public List<String> selectRecoverableExecutionIds() {
    return executionMapper.selectRecoverableExecutionIds(currentProjectId());
  }

  @Override
  public List<ProjectExecutionRef> selectRecoverableExecutionsForDispatch() {
    return executionMapper.selectRecoverableExecutionsForDispatch().stream()
        .filter(value -> value.getProjectId() != null && value.getProjectId() > 0L)
        .map(value -> new ProjectExecutionRef(value.getProjectId(), value.getId()))
        .toList();
  }

  @Override
  public long countActiveExecutions(String workflowId) {
    long projectId = currentProjectId();
    if (!workflowAccessible(workflowId, projectId)) return 0L;
    return executionMapper.countActiveExecutions(workflowId, projectId);
  }

  @Override
  public String selectEffectiveRuntimeMetadata(String executionId) {
    return executionMapper.selectEffectiveRuntimeMetadata(executionId, currentProjectId());
  }

  @Override
  public String selectAuditCarrierJson(String executionId) {
    return executionMapper.selectAuditCarrierJson(executionId, currentProjectId());
  }

  @Override
  public int updateAuditCarrier(String executionId, String carrierJson) {
    return executionMapper.updateAuditCarrier(executionId, currentProjectId(), carrierJson);
  }

  @Override
  public WorkflowNodeAttemptPO selectAttempt(String attemptId) {
    currentProjectId();
    WorkflowNodeAttemptPO attempt = nodeAttemptMapper.selectById(attemptId);
    if (attempt == null || selectExecution(attempt.getWorkflowExecutionId()) == null) return null;
    return attempt;
  }

  @Override
  public int bindExternalExecution(String attemptId, String externalExecutionId) {
    if (selectAttempt(attemptId) == null) return 0;
    return nodeAttemptMapper.bindExternalExecution(attemptId, externalExecutionId);
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private WorkflowExecutionPO requireExecution(String executionId) {
    WorkflowExecutionPO execution = selectExecution(executionId);
    if (execution == null) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return execution;
  }

  private boolean workflowAccessible(String workflowId, long projectId) {
    if (definitionMapper == null || workflowId == null || workflowId.isBlank()) return false;
    return definitionMapper.selectCount(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId)) > 0L;
  }
}
