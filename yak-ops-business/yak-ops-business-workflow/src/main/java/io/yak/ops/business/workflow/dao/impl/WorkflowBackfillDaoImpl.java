package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowBackfillMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** Backfill DAO；普通 CRUD fail-closed，跨 Project 只允许显式 reconciliation dispatcher。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowBackfillDaoImpl implements WorkflowBackfillDao {
  private final WorkflowBackfillMapper mapper;
  private final WorkflowDefinitionMapper definitionMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowBackfillDaoImpl(
      WorkflowBackfillMapper mapper,
      WorkflowDefinitionMapper definitionMapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.definitionMapper = definitionMapper;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor. Calls still fail closed without CurrentProject. */
  public WorkflowBackfillDaoImpl(WorkflowBackfillMapper mapper) {
    this(mapper, null, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public int insert(WorkflowBackfillPO backfill) {
    backfill.setProjectId(requireOwnedProject(backfill.getProjectId(), backfill.getWorkflowId()));
    return mapper.insert(backfill);
  }

  @Override
  public int update(WorkflowBackfillPO backfill) {
    long projectId = requireOwnedProject(backfill.getProjectId(), backfill.getWorkflowId());
    backfill.setProjectId(projectId);
    return mapper.update(
        backfill,
        Wrappers.<WorkflowBackfillPO>lambdaUpdate()
            .eq(WorkflowBackfillPO::getId, backfill.getId())
            .eq(WorkflowBackfillPO::getProjectId, projectId));
  }

  @Override
  public WorkflowBackfillPO select(String id) {
    long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowBackfillPO>lambdaQuery()
            .eq(WorkflowBackfillPO::getId, id)
            .eq(WorkflowBackfillPO::getProjectId, projectId));
  }

  @Override
  public List<WorkflowBackfillPO> selectList(String workflowId, String scheduleId) {
    long projectId = currentProjectId();
    var query = Wrappers.<WorkflowBackfillPO>lambdaQuery()
        .eq(WorkflowBackfillPO::getProjectId, projectId);
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowBackfillPO::getWorkflowId, workflowId.trim());
    }
    if (scheduleId != null && !scheduleId.isBlank()) {
      query.eq(WorkflowBackfillPO::getScheduleId, scheduleId.trim());
    }
    return mapper.selectList(query.orderByDesc(WorkflowBackfillPO::getCreateTime));
  }

  @Override
  public List<ProjectBackfillRef> selectRunningForReconciliation() {
    return mapper.selectList(
            Wrappers.<WorkflowBackfillPO>lambdaQuery()
                .select(WorkflowBackfillPO::getId, WorkflowBackfillPO::getProjectId)
                .eq(WorkflowBackfillPO::getStatus, "RUNNING")
                .isNotNull(WorkflowBackfillPO::getProjectId)
                .orderByAsc(WorkflowBackfillPO::getProjectId)
                .orderByAsc(WorkflowBackfillPO::getCreateTime))
        .stream()
        .filter(value -> value.getProjectId() != null && value.getProjectId() > 0L)
        .map(value -> new ProjectBackfillRef(value.getProjectId(), value.getId()))
        .toList();
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private long requireOwnedProject(Long storedProjectId, String workflowId) {
    long projectId = currentProjectId();
    if (storedProjectId != null && !Objects.equals(storedProjectId, projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    if (definitionMapper == null || !workflowOwned(workflowId, projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return projectId;
  }

  private boolean workflowOwned(String workflowId, long projectId) {
    if (workflowId == null || workflowId.isBlank()) return false;
    return definitionMapper.selectCount(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId)) > 0L;
  }
}
