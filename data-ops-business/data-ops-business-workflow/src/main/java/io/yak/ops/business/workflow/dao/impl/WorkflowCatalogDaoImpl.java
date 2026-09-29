package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowVersionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 基于 MyBatis-Plus 的工作流定义与版本 DAO；普通业务路径必须绑定 trusted CurrentProject。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowCatalogDaoImpl implements WorkflowCatalogDao {
  private final WorkflowDefinitionMapper definitionMapper;
  private final WorkflowVersionMapper versionMapper;
  private final WorkflowScheduleMapper scheduleMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowCatalogDaoImpl(
      WorkflowDefinitionMapper definitionMapper,
      WorkflowVersionMapper versionMapper,
      WorkflowScheduleMapper scheduleMapper,
      CurrentProject currentProject) {
    this.definitionMapper = definitionMapper;
    this.versionMapper = versionMapper;
    this.scheduleMapper = scheduleMapper;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor. All operations still fail closed without CurrentProject. */
  public WorkflowCatalogDaoImpl(
      WorkflowDefinitionMapper definitionMapper,
      WorkflowVersionMapper versionMapper,
      WorkflowScheduleMapper scheduleMapper) {
    this(
        definitionMapper,
        versionMapper,
        scheduleMapper,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public List<WorkflowDefinitionPO> selectDefinitions() {
    long projectId = currentProjectId();
    return definitionMapper.selectList(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getProjectId, projectId)
            .orderByDesc(WorkflowDefinitionPO::getUpdateTime));
  }

  @Override
  public List<WorkflowVersionPO> selectPublishedVersions(String workflowId) {
    long projectId = currentProjectId();
    if (!accessible(workflowId, projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return versionMapper.selectList(
        Wrappers.<WorkflowVersionPO>lambdaQuery()
            .eq(WorkflowVersionPO::getProjectId, projectId)
            .eq(WorkflowVersionPO::getWorkflowId, workflowId)
            .orderByAsc(WorkflowVersionPO::getVersionNo));
  }

  @Override
  public String lockDefinition(String workflowId) {
    long projectId = currentProjectId();
    if (!accessible(workflowId, projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return definitionMapper.lockWorkflow(workflowId);
  }

  @Override
  public WorkflowVersionPO selectVersionById(String versionId) {
    return versionMapper.selectByIdAndProject(versionId, currentProjectId());
  }

  @Override
  public int upsertDefinition(WorkflowDefinitionPO definition) {
    long projectId = currentProjectId();
    WorkflowDefinitionPO existing = definitionMapper.selectById(definition.getId());
    if (existing != null
        && (existing.getProjectId() == null || !Objects.equals(existing.getProjectId(), projectId))) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    if (definition.getProjectId() != null && !Objects.equals(definition.getProjectId(), projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    definition.setProjectId(projectId);
    return definitionMapper.upsert(definition);
  }

  @Override
  public int insertVersion(WorkflowVersionPO version) {
    long projectId = currentProjectId();
    if (version.getProjectId() != null && !Objects.equals(version.getProjectId(), projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    String workflowId = normalize(version.getWorkflowId());
    if (workflowId != null && !accessible(workflowId, projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    if (workflowId == null && !"RUNTIME".equalsIgnoreCase(version.getVersionKind())) {
      throw new IllegalArgumentException("Only RUNTIME workflow versions may omit workflowId");
    }
    version.setWorkflowId(workflowId);
    version.setProjectId(projectId);
    return versionMapper.insert(version);
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public int deleteDefinition(String workflowId) {
    long projectId = currentProjectId();
    if (!accessible(workflowId, projectId)) return 0;
    List<String> scheduleIds = scheduleMapper.selectObjs(
            Wrappers.<WorkflowSchedulePO>query()
                .select("id")
                .eq("workflow_id", workflowId)
                .eq("project_id", projectId))
        .stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();

    int deleted = definitionMapper.delete(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId));
    if (deleted != 1 || scheduleIds.isEmpty()) return deleted;

    scheduleMapper.delete(
        Wrappers.<WorkflowSchedulePO>lambdaQuery()
            .in(WorkflowSchedulePO::getId, scheduleIds)
            .eq(WorkflowSchedulePO::getProjectId, projectId));
    return deleted;
  }

  @Override
  public int initializeEngineDefinition(String versionId, String engineDefinitionJson) {
    long projectId = currentProjectId();
    return versionMapper.initializeEngineDefinition(versionId, projectId, engineDefinitionJson);
  }

  @Override
  public int initializeRuntimeMetadata(String versionId, String runtimeMetadataJson) {
    long projectId = currentProjectId();
    return versionMapper.initializeRuntimeMetadata(versionId, projectId, runtimeMetadataJson);
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private boolean accessible(String workflowId, long projectId) {
    if (workflowId == null || workflowId.isBlank()) return false;
    return definitionMapper.selectCount(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId)) > 0L;
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
