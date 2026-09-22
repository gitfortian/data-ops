package io.yak.ops.business.sync.realtime.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.realtime.dao.RealtimeJobDao;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobCommandMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDeploymentMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobEventMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobQueryMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobEventPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobListRow;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** Realtime persistence adapter. Ordinary business access is fail-closed on CurrentProject. */
@Repository
@DependsOn("realtimeSyncFlyway")
public class RealtimeJobDaoImpl implements RealtimeJobDao {

  private final RealtimeJobDefinitionMapper definitionMapper;
  private final RealtimeJobDeploymentMapper deploymentMapper;
  private final RealtimeJobEventMapper eventMapper;
  private final RealtimeJobCommandMapper commandMapper;
  private final RealtimeJobQueryMapper queryMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public RealtimeJobDaoImpl(
      RealtimeJobDefinitionMapper definitionMapper,
      RealtimeJobDeploymentMapper deploymentMapper,
      RealtimeJobEventMapper eventMapper,
      RealtimeJobCommandMapper commandMapper,
      RealtimeJobQueryMapper queryMapper,
      CurrentProject currentProject) {
    this.definitionMapper = definitionMapper;
    this.deploymentMapper = deploymentMapper;
    this.eventMapper = eventMapper;
    this.commandMapper = commandMapper;
    this.queryMapper = queryMapper;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor; ordinary calls remain fail-closed without CurrentProject. */
  public RealtimeJobDaoImpl(
      RealtimeJobDefinitionMapper definitionMapper,
      RealtimeJobDeploymentMapper deploymentMapper,
      RealtimeJobEventMapper eventMapper,
      RealtimeJobCommandMapper commandMapper,
      RealtimeJobQueryMapper queryMapper) {
    this(
        definitionMapper,
        deploymentMapper,
        eventMapper,
        commandMapper,
        queryMapper,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public long insertDefinition(RealtimeJobDefinitionPO definition) {
    bindCurrentProject(definition);
    definitionMapper.insert(definition);
    if (definition.getId() == null) throw new IllegalStateException("新增实时任务未返回主键");
    return definition.getId();
  }

  @Override
  public int updateDefinition(
      long id,
      String name,
      String description,
      String specJson,
      String digest,
      long environmentId) {
    long projectId = currentProjectId();
    return definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, id)
            .eq(RealtimeJobDefinitionPO::getProjectId, projectId)
            .set(RealtimeJobDefinitionPO::getJobName, name)
            .set(RealtimeJobDefinitionPO::getDescription, description)
            .set(RealtimeJobDefinitionPO::getRuntimeEnvironmentId, environmentId)
            .set(RealtimeJobDefinitionPO::getSpecJson, specJson)
            .set(RealtimeJobDefinitionPO::getConfigDigest, digest)
            .setSql("definition_version = definition_version + 1")
            .set(RealtimeJobDefinitionPO::getReleaseState, "DRAFT"));
  }

  @Override
  public int publish(long id, int expectedDefinitionVersion, String expectedDigest) {
    long projectId = currentProjectId();
    return definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, id)
            .eq(RealtimeJobDefinitionPO::getProjectId, projectId)
            .eq(RealtimeJobDefinitionPO::getDefinitionVersion, expectedDefinitionVersion)
            .eq(RealtimeJobDefinitionPO::getConfigDigest, expectedDigest)
            .set(RealtimeJobDefinitionPO::getReleaseState, "PUBLISHED")
            .set(RealtimeJobDefinitionPO::getPublishedVersion, expectedDefinitionVersion));
  }

  @Override
  public Optional<RealtimeJobDefinitionPO> findDefinition(long id) {
    long projectId = currentProjectId();
    return Optional.ofNullable(
        definitionMapper.selectOne(
            Wrappers.<RealtimeJobDefinitionPO>lambdaQuery()
                .eq(RealtimeJobDefinitionPO::getId, id)
                .eq(RealtimeJobDefinitionPO::getProjectId, projectId)));
  }

  @Override
  public Optional<RealtimeJobDefinitionPO> lockDefinition(long id) {
    return Optional.ofNullable(commandMapper.lockDefinitionByProject(id, currentProjectId()));
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> deploymentByIdempotencyKey(String key) {
    long projectId = currentProjectId();
    return deploymentMapper.selectList(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getIdempotencyKey, key)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> latestDeployment(long definitionId) {
    long projectId = currentProjectId();
    return deploymentMapper.selectList(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .orderByDesc(RealtimeJobDeploymentPO::getId)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> findDeployment(long deploymentId) {
    long projectId = currentProjectId();
    return Optional.ofNullable(
        deploymentMapper.selectOne(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getProjectId, projectId)));
  }

  @Override
  public long insertDeployment(RealtimeJobDeploymentPO deployment) {
    deployment.setProjectId(resolveDeploymentProject(deployment));
    deploymentMapper.insert(deployment);
    if (deployment.getId() == null) throw new IllegalStateException("新增部署未返回主键");
    return deployment.getId();
  }

  @Override
  public void bindDeploymentDefinitionVersion(
      long deploymentId, long definitionVersionId, int sourceDraftRevision) {
    long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getStatus, "SUBMITTING")
                .isNull(RealtimeJobDeploymentPO::getDefinitionVersionId)
                .set(RealtimeJobDeploymentPO::getDefinitionVersionId, definitionVersionId)
                .set(RealtimeJobDeploymentPO::getDefinitionVersion, sourceDraftRevision));
    if (updated != 1) throw new IllegalStateException("部署绑定 Published DefinitionVersion 失败：" + deploymentId);
  }

  @Override
  public int markDeploymentRunning(
      long definitionId, long deploymentId, String engineJobId, String runtimeRevision) {
    long projectId = currentProjectId();
    return deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
            .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
            .eq(RealtimeJobDeploymentPO::getDesiredState, "RUNNING")
            .eq(RealtimeJobDeploymentPO::getObservedState, "STARTING")
            .set(RealtimeJobDeploymentPO::getGatewayJobId, engineJobId)
            .set(RealtimeJobDeploymentPO::getRuntimeVersion, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getRuntimeRevision, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getObservedState, "RUNNING")
            .set(RealtimeJobDeploymentPO::getStatus, "RUNNING")
            .set(RealtimeJobDeploymentPO::getResultUncertain, false)
            .set(RealtimeJobDeploymentPO::getErrorMessage, null));
  }

  @Override
  public void bindDeploymentForStop(long deploymentId, String engineJobId, String runtimeRevision) {
    long projectId = currentProjectId();
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
            .set(RealtimeJobDeploymentPO::getGatewayJobId, engineJobId)
            .set(RealtimeJobDeploymentPO::getRuntimeVersion, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getRuntimeRevision, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getStatus, "STOPPING")
            .set(RealtimeJobDeploymentPO::getResultUncertain, false)
            .set(RealtimeJobDeploymentPO::getErrorMessage, null));
  }

  @Override
  public void markDeployFailure(
      long definitionId,
      long deploymentId,
      boolean uncertain,
      boolean stopRequested,
      String message) {
    String desiredState = stopRequested ? "STOPPED" : (uncertain ? "RUNNING" : "STOPPED");
    String observedState = uncertain ? "UNKNOWN" : "FAILED";
    long projectId = currentProjectId();
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
            .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
            .set(RealtimeJobDeploymentPO::getDesiredState, desiredState)
            .set(RealtimeJobDeploymentPO::getObservedState, observedState)
            .set(RealtimeJobDeploymentPO::getStatus, observedState)
            .set(RealtimeJobDeploymentPO::getResultUncertain, uncertain)
            .set(RealtimeJobDeploymentPO::getErrorMessage, message));
  }

  @Override
  public void markStopping(long definitionId, Long deploymentId) {
    if (deploymentId == null) return;
    long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
                .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
                .set(RealtimeJobDeploymentPO::getObservedState, "STOPPING")
                .set(RealtimeJobDeploymentPO::getStatus, "STOPPING"));
    if (updated != 1) throw new IllegalStateException("Execution 不存在或已变化，无法记录停止意图：" + deploymentId);
  }

  @Override
  public void reserveReplacementStop(
      long definitionId,
      long deploymentId,
      String commandType,
      long targetDefinitionVersionId,
      String idempotencyKey) {
    long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getDesiredState, "RUNNING")
                .eq(RealtimeJobDeploymentPO::getObservedState, "RUNNING")
                .eq(RealtimeJobDeploymentPO::getResultUncertain, false)
                .isNull(RealtimeJobDeploymentPO::getReplacementIdempotencyKey)
                .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
                .set(RealtimeJobDeploymentPO::getObservedState, "STOPPING")
                .set(RealtimeJobDeploymentPO::getStatus, "STOPPING")
                .set(RealtimeJobDeploymentPO::getReplacementCommandType, commandType)
                .set(RealtimeJobDeploymentPO::getReplacementTargetDefinitionVersionId, targetDefinitionVersionId)
                .set(RealtimeJobDeploymentPO::getReplacementIdempotencyKey, idempotencyKey));
    if (updated != 1) throw new IllegalStateException("Execution 已变化，无法预留版本替换命令：" + deploymentId);
  }

  @Override
  public void clearReplacementIntent(long deploymentId, String idempotencyKey) {
    long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getReplacementIdempotencyKey, idempotencyKey)
                .set(RealtimeJobDeploymentPO::getReplacementCommandType, null)
                .set(RealtimeJobDeploymentPO::getReplacementTargetDefinitionVersionId, null)
                .set(RealtimeJobDeploymentPO::getReplacementIdempotencyKey, null));
    if (updated != 1) throw new IllegalStateException("版本替换命令完成标记失败：" + deploymentId);
  }

  @Override
  public void reconcile(
      long definitionId,
      Long deploymentId,
      String observedState,
      String deploymentState,
      String engineJobId,
      String error) {
    if (deploymentId == null) return;
    int updated = commandMapper.reconcileDeploymentByProject(
        deploymentId,
        currentProjectId(),
        observedState,
        deploymentState,
        engineJobId,
        error);
    if (updated != 1) throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
  }

  @Override
  public void markTerminalFailure(long definitionId, Long deploymentId, String message) {
    if (deploymentId == null) return;
    long projectId = currentProjectId();
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
            .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
            .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
            .set(RealtimeJobDeploymentPO::getObservedState, "FAILED")
            .set(RealtimeJobDeploymentPO::getStatus, "FAILED")
            .set(RealtimeJobDeploymentPO::getErrorMessage, message));
  }

  @Override
  public List<RealtimeJobDeploymentPO> reconcileExecutions() {
    return commandMapper.reconcileExecutionsByProject(currentProjectId());
  }

  @Override
  public List<ProjectDeploymentRef> findReconcileCandidatesForDispatch() {
    return commandMapper.reconcileExecutionsForDispatch().stream()
        .map(this::dispatcherRef)
        .toList();
  }

  @Override
  public int deleteDefinition(long id) {
    long projectId = currentProjectId();
    int deleted = definitionMapper.delete(
        Wrappers.<RealtimeJobDefinitionPO>lambdaQuery()
            .eq(RealtimeJobDefinitionPO::getId, id)
            .eq(RealtimeJobDefinitionPO::getProjectId, projectId));
    if (deleted != 1) return deleted;
    eventMapper.delete(
        Wrappers.<RealtimeJobEventPO>lambdaQuery()
            .eq(RealtimeJobEventPO::getDefinitionId, id));
    deploymentMapper.delete(
        Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
            .eq(RealtimeJobDeploymentPO::getDefinitionId, id)
            .eq(RealtimeJobDeploymentPO::getProjectId, projectId));
    return deleted;
  }

  @Override
  public void insertEvent(RealtimeJobEventPO event) {
    requireDefinitionOwned(event.getDefinitionId());
    if (event.getDeploymentId() != null) {
      RealtimeJobDeploymentPO deployment = findDeployment(event.getDeploymentId())
          .orElseThrow(() -> new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND));
      if (!Objects.equals(deployment.getDefinitionId(), event.getDefinitionId())) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
    }
    eventMapper.insert(event);
  }

  @Override
  public boolean tryAcquireReconcileLease(String owner, int leaseSeconds) {
    return commandMapper.tryAcquireLease(owner, Math.max(5, leaseSeconds)) == 1;
  }

  @Override
  public List<RealtimeJobEventPO> events(long definitionId) {
    requireDefinitionOwned(definitionId);
    return eventMapper.selectList(
        Wrappers.<RealtimeJobEventPO>lambdaQuery()
            .eq(RealtimeJobEventPO::getDefinitionId, definitionId)
            .orderByDesc(RealtimeJobEventPO::getId)
            .last("LIMIT 200"));
  }

  @Override
  public int bindRuntimeIdentity(String idempotencyKey, String runtimeJobName) {
    long projectId = currentProjectId();
    return deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getProjectId, projectId)
            .eq(RealtimeJobDeploymentPO::getIdempotencyKey, idempotencyKey)
            .isNull(RealtimeJobDeploymentPO::getGatewayJobId)
            .eq(RealtimeJobDeploymentPO::getRuntimeIdentityState, "REQUIRED")
            .and(q -> q.isNull(RealtimeJobDeploymentPO::getRuntimeJobName)
                .or()
                .eq(RealtimeJobDeploymentPO::getRuntimeJobName, runtimeJobName))
            .set(RealtimeJobDeploymentPO::getRuntimeJobName, runtimeJobName)
            .set(RealtimeJobDeploymentPO::getRuntimeIdentityState, "BOUND"));
  }

  @Override
  public Optional<String> runtimeJobName(long deploymentId) {
    return findDeployment(deploymentId)
        .map(RealtimeJobDeploymentPO::getRuntimeJobName)
        .filter(value -> value != null && !value.isBlank());
  }

  @Override
  public long countPage(String keyword, Long id, String releaseState, String stateGroup) {
    return queryMapper.countByProject(currentProjectId(), keyword, id, releaseState, stateGroup);
  }

  @Override
  public List<RealtimeJobListRow> page(
      String keyword,
      Long id,
      String releaseState,
      String stateGroup,
      int limit,
      int offset) {
    return queryMapper.pageByProject(
        currentProjectId(), keyword, id, releaseState, stateGroup, limit, offset);
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private void bindCurrentProject(RealtimeJobDefinitionPO definition) {
    long projectId = currentProjectId();
    if (definition.getProjectId() != null && !Objects.equals(projectId, definition.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    definition.setProjectId(projectId);
  }

  private long resolveDeploymentProject(RealtimeJobDeploymentPO deployment) {
    long projectId = currentProjectId();
    if (deployment.getProjectId() != null && !Objects.equals(projectId, deployment.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    requireDefinitionOwned(deployment.getDefinitionId());
    return projectId;
  }

  private void requireDefinitionOwned(long definitionId) {
    if (findDefinition(definitionId).isEmpty()) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  private ProjectDeploymentRef dispatcherRef(RealtimeJobDeploymentPO deployment) {
    Long projectId = deployment.getProjectId();
    Long definitionId = deployment.getDefinitionId();
    Long deploymentId = deployment.getId();
    if (projectId == null || projectId <= 0L
        || definitionId == null || definitionId <= 0L
        || deploymentId == null || deploymentId <= 0L) {
      throw new IllegalStateException("Realtime dispatcher candidate 缺少 durable Project identity");
    }
    return new ProjectDeploymentRef(projectId, definitionId, deploymentId);
  }
}
