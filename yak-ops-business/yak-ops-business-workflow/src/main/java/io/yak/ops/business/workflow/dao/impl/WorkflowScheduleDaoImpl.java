package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 工作流调度定义 DAO；普通 CRUD fail-closed，跨 Project 仅允许显式 startup dispatcher。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowScheduleDaoImpl implements WorkflowScheduleDao {
  private final WorkflowScheduleMapper mapper;
  private final WorkflowDefinitionMapper definitionMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowScheduleDaoImpl(
      WorkflowScheduleMapper mapper,
      WorkflowDefinitionMapper definitionMapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.definitionMapper = definitionMapper;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor. Calls still fail closed without CurrentProject. */
  public WorkflowScheduleDaoImpl(WorkflowScheduleMapper mapper) {
    this(mapper, null, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public List<WorkflowSchedulePO> selectSchedules(String workflowId, String status) {
    long projectId = currentProjectId();
    var query = Wrappers.<WorkflowSchedulePO>lambdaQuery()
        .eq(WorkflowSchedulePO::getProjectId, projectId);
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowSchedulePO::getWorkflowId, workflowId.trim());
    }
    if (status != null && !status.isBlank()) {
      query.eq(WorkflowSchedulePO::getStatus, status.trim().toUpperCase());
    }
    return mapper.selectList(query.orderByDesc(WorkflowSchedulePO::getUpdateTime));
  }

  @Override
  public WorkflowSchedulePO selectSchedule(String id) {
    long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowSchedulePO>lambdaQuery()
            .eq(WorkflowSchedulePO::getId, id)
            .eq(WorkflowSchedulePO::getProjectId, projectId));
  }

  @Override
  public int insertSchedule(WorkflowSchedulePO schedule) {
    schedule.setProjectId(requireOwnedProject(schedule.getProjectId(), schedule.getWorkflowId()));
    return mapper.insert(schedule);
  }

  @Override
  public int updateSchedule(WorkflowSchedulePO schedule) {
    long projectId = requireOwnedProject(schedule.getProjectId(), schedule.getWorkflowId());
    schedule.setProjectId(projectId);
    return mapper.update(
        schedule,
        Wrappers.<WorkflowSchedulePO>lambdaUpdate()
            .eq(WorkflowSchedulePO::getId, schedule.getId())
            .eq(WorkflowSchedulePO::getProjectId, projectId));
  }

  @Override
  public int updateRuntimeState(String id, Instant lastFireTime, Instant nextFireTime) {
    long projectId = currentProjectId();
    return mapper.update(
        null,
        Wrappers.<WorkflowSchedulePO>lambdaUpdate()
            .eq(WorkflowSchedulePO::getId, id)
            .eq(WorkflowSchedulePO::getProjectId, projectId)
            .set(WorkflowSchedulePO::getLastFireTime, lastFireTime)
            .set(WorkflowSchedulePO::getNextFireTime, nextFireTime));
  }

  @Override
  public int deleteSchedule(String id) {
    long projectId = currentProjectId();
    return mapper.delete(
        Wrappers.<WorkflowSchedulePO>lambdaQuery()
            .eq(WorkflowSchedulePO::getId, id)
            .eq(WorkflowSchedulePO::getProjectId, projectId));
  }

  @Override
  public List<ProjectScheduleRef> selectSchedulesForReconciliation() {
    return mapper.selectList(
            Wrappers.<WorkflowSchedulePO>lambdaQuery()
                .select(WorkflowSchedulePO::getId, WorkflowSchedulePO::getProjectId)
                .isNotNull(WorkflowSchedulePO::getProjectId)
                .orderByAsc(WorkflowSchedulePO::getProjectId)
                .orderByAsc(WorkflowSchedulePO::getId))
        .stream()
        .filter(value -> value.getProjectId() != null && value.getProjectId() > 0L)
        .map(value -> new ProjectScheduleRef(value.getProjectId(), value.getId()))
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
