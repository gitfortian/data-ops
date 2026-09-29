package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.dao.RealtimeJobDao;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobEventPO;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobChangeEvent;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.repository.support.RealtimeJsonCodec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

@Repository
public class RealtimeJobStoreAdapter implements RealtimeJobStore {

  private final RealtimeJobDao dao;
  private final RealtimeJsonCodec json;
  private final RealtimeJobListQuery listQuery;
  private final ApplicationEventPublisher events;

  public RealtimeJobStoreAdapter(
      RealtimeJobDao dao,
      RealtimeJsonCodec json,
      RealtimeJobListQuery listQuery,
      ApplicationEventPublisher events) {
    this.dao = dao;
    this.json = json;
    this.listQuery = listQuery;
    this.events = events;
  }

  @Override
  public long insertDefinition(
      String name,
      String description,
      CdcPipelineSpec spec,
      String digest,
      long runtimeEnvironmentId) {
    RealtimeJobDefinitionPO po = new RealtimeJobDefinitionPO();
    po.setJobName(name);
    po.setDescription(description);
    po.setRuntimeEnvironmentId(runtimeEnvironmentId);
    po.setSpecJson(json.write(spec));
    po.setConfigDigest(digest);
    return dao.insertDefinition(po);
  }

  @Override
  public void updateDefinition(
      long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      String digest,
      long runtimeEnvironmentId) {
    if (dao.updateDefinition(id, name, description, json.write(spec), digest, runtimeEnvironmentId)
        != 1) {
      throw new IllegalStateException("实时同步任务已变化或不存在，请刷新后重试");
    }
  }

  @Override
  public void publish(long id, int expectedDefinitionVersion, String expectedDigest) {
    if (dao.publish(id, expectedDefinitionVersion, expectedDigest) != 1) {
      throw new IllegalStateException("任务状态或定义版本已变化，请刷新后重新校验并发布");
    }
  }

  @Override
  public Optional<DefinitionRow> definition(long id) {
    return dao.findDefinition(id).map(this::definitionRow);
  }

  @Override
  public DefinitionRow lockDefinition(long id) {
    return dao.lockDefinition(id)
        .map(this::definitionRow)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
  }

  @Override
  public RealtimeJobPage page(int pageNo, int pageSize, String keyword) {
    return listQuery.page(pageNo, pageSize, keyword, null, null, null);
  }

  @Override
  public Optional<PublishedDefinitionRow> publishedDefinition(long definitionId) {
    return Optional.empty();
  }

  @Override
  public Optional<DeploymentRow> deploymentByIdempotencyKey(String key) {
    return dao.deploymentByIdempotencyKey(key).map(this::deploymentRow);
  }

  @Override
  public Optional<DeploymentRow> latestDeployment(long definitionId) {
    return dao.latestDeployment(definitionId).map(this::deploymentRow);
  }

  @Override
  public Optional<ComputeEnvironmentSnapshot> deploymentEnvironment(long deploymentId) {
    return dao.findDeployment(deploymentId)
        .map(RealtimeJobDeploymentPO::getRuntimeEnvironmentSnapshotJson)
        .filter(value -> value != null && !value.isBlank())
        .map(json::readEnvironmentSnapshot);
  }

  @Override
  public long insertDeployment(
      DefinitionRow definition,
      CdcPipelineSpec spec,
      String summary,
      String artifactDigest,
      ComputeEnvironmentSnapshot environment,
      String idempotencyKey) {
    if (environment == null) {
      throw new IllegalArgumentException("实时同步 Execution 必须绑定运行环境快照");
    }
    RealtimeJobDeploymentPO po = new RealtimeJobDeploymentPO();
    po.setDefinitionId(definition.id());
    po.setDefinitionVersion(definition.draftRevision());
    po.setRuntimeEnvironmentId(environment.id());
    po.setRuntimeEnvironmentVersion(environment.version());
    po.setRuntimeEnvironmentSnapshotJson(json.write(environment));
    po.setSpecSnapshotJson(json.write(spec));
    po.setSpecSummary(summary);
    po.setConfigDigest(artifactDigest);
    po.setIdempotencyKey(idempotencyKey);
    po.setEngineType("FLINK_CDC");
    po.setDesiredState("RUNNING");
    po.setObservedState("STARTING");
    po.setRuntimeIdentityState("REQUIRED");
    po.setStatus("SUBMITTING");
    po.setResultUncertain(false);
    return dao.insertDeployment(po);
  }

  @Override
  public void bindDeploymentDefinitionVersion(
      long deploymentId, long definitionVersionId, int sourceDraftRevision) {
    dao.bindDeploymentDefinitionVersion(deploymentId, definitionVersionId, sourceDraftRevision);
  }

  @Override
  public void markDeploymentRunning(
      long definitionId,
      long deploymentId,
      String engineJobId,
      String runtimeRevision) {
    if (dao.markDeploymentRunning(definitionId, deploymentId, engineJobId, runtimeRevision) != 1) {
      throw new IllegalStateException("Execution 状态已变化，不能覆盖当前运行意图");
    }
  }

  @Override
  public void bindDeploymentForStop(
      long deploymentId, String engineJobId, String runtimeRevision) {
    dao.bindDeploymentForStop(deploymentId, engineJobId, runtimeRevision);
  }

  @Override
  public void markDeployFailure(
      long definitionId,
      long deploymentId,
      boolean uncertain,
      boolean stopRequested,
      String message) {
    dao.markDeployFailure(
        definitionId, deploymentId, uncertain, stopRequested, message);
  }

  @Override
  public void markStopping(long definitionId, Long deploymentId) {
    dao.markStopping(definitionId, deploymentId);
  }

  @Override
  public void reserveReplacementStop(
      long definitionId,
      long deploymentId,
      String commandType,
      long targetDefinitionVersionId,
      String idempotencyKey) {
    dao.reserveReplacementStop(
        definitionId,
        deploymentId,
        commandType,
        targetDefinitionVersionId,
        idempotencyKey);
  }

  @Override
  public void clearReplacementIntent(long deploymentId, String idempotencyKey) {
    dao.clearReplacementIntent(deploymentId, idempotencyKey);
  }

  @Override
  public void reconcile(
      long definitionId,
      Long deploymentId,
      String observedState,
      String deploymentState,
      String engineJobId,
      String error) {
    dao.reconcile(
        definitionId, deploymentId, observedState, deploymentState, engineJobId, error);
  }

  @Override
  public void markTerminalFailure(long definitionId, Long deploymentId, String message) {
    dao.markTerminalFailure(definitionId, deploymentId, message);
  }

  @Override
  public List<DeploymentRow> reconcileCandidates() {
    return dao.reconcileExecutions().stream().map(this::deploymentRow).toList();
  }

  @Override
  public void delete(long id) {
    if (dao.deleteDefinition(id) != 1) {
      throw new IllegalStateException("实时同步任务不存在或已被其他操作删除");
    }
  }

  @Override
  public void event(
      long definitionId,
      Long deploymentId,
      String type,
      String from,
      String to,
      String message) {
    RealtimeJobEventPO po = new RealtimeJobEventPO();
    po.setDefinitionId(definitionId);
    po.setDeploymentId(deploymentId);
    po.setEventType(type);
    po.setFromState(from);
    po.setToState(to);
    po.setMessage(message);
    dao.insertEvent(po);
    events.publishEvent(new RealtimeJobChangeEvent(definitionId, type, from, to, message));
  }

  @Override
  public boolean tryAcquireReconcileLease(String owner, int leaseSeconds) {
    return dao.tryAcquireReconcileLease(owner, leaseSeconds);
  }

  @Override
  public List<RealtimeJobEventView> events(long definitionId) {
    return dao.events(definitionId).stream()
        .map(
            po ->
                new RealtimeJobEventView(
                    po.getId(),
                    po.getDeploymentId(),
                    po.getEventType(),
                    po.getFromState(),
                    po.getToState(),
                    po.getMessage(),
                    po.getCreateTime()))
        .toList();
  }

  @Override
  public RealtimeJobView view(long id) {
    DefinitionRow definition =
        definition(id)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    DeploymentRow latest = latestDeployment(id).orElse(null);
    String desired = latest == null ? "STOPPED" : latest.desiredState();
    String observed = latest == null ? "STOPPED" : latest.observedState();
    String error = latest == null ? null : latest.errorMessage();
    boolean publishedUpdateAvailable = publishedUpdateAvailable(definition, latest);
    return new RealtimeJobView(
        definition.id(),
        definition.name(),
        definition.description(),
        definition.spec(),
        definition.runtimeEnvironmentId(),
        definition.releaseState(),
        desired,
        observed,
        definition.draftRevision(),
        definition.publishedDraftRevision(),
        definition.sourceConfigDigest(),
        error,
        definition.createTime(),
        definition.updateTime(),
        publishedUpdateAvailable,
        deploymentView(latest));
  }

  @Override
  public RealtimeJobView.Deployment deploymentView(DeploymentRow deployment) {
    if (deployment == null) return null;
    return new RealtimeJobView.Deployment(
        deployment.id(),
        deployment.sourceDraftRevision(),
        deployment.specSummary(),
        deployment.artifactDigest(),
        deployment.idempotencyKey(),
        deployment.engineJobId(),
        deployment.runtimeRevision(),
        deployment.runtimeEnvironment(),
        deployment.status(),
        deployment.resultUncertain(),
        deployment.errorMessage(),
        deployment.createTime(),
        deployment.updateTime());
  }

  private DefinitionRow definitionRow(RealtimeJobDefinitionPO po) {
    return new DefinitionRow(
        po.getId(),
        po.getJobName(),
        po.getDescription(),
        json.readSpec(po.getSpecJson()),
        po.getRuntimeEnvironmentId(),
        po.getReleaseState(),
        "STOPPED",
        "STOPPED",
        po.getDefinitionVersion() == null ? 0 : po.getDefinitionVersion(),
        po.getPublishedVersion(),
        po.getPublishedDefinitionVersionId(),
        po.getConfigDigest(),
        null,
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private DeploymentRow deploymentRow(RealtimeJobDeploymentPO po) {
    return new DeploymentRow(
        po.getId(),
        po.getDefinitionId(),
        po.getDefinitionVersionId(),
        po.getDefinitionVersion() == null ? 0 : po.getDefinitionVersion(),
        json.readSpec(po.getSpecSnapshotJson()),
        po.getSpecSummary(),
        po.getConfigDigest(),
        po.getIdempotencyKey(),
        po.getGatewayJobId(),
        po.getRuntimeRevision(),
        json.readEnvironmentSnapshot(po.getRuntimeEnvironmentSnapshotJson()),
        po.getEngineType(),
        po.getDesiredState(),
        po.getObservedState(),
        po.getStatus(),
        Boolean.TRUE.equals(po.getResultUncertain()),
        po.getErrorMessage(),
        po.getReplacementCommandType(),
        po.getReplacementTargetDefinitionVersionId(),
        po.getReplacementIdempotencyKey(),
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private boolean publishedUpdateAvailable(DefinitionRow definition, DeploymentRow latest) {
    if (latest == null
        || !"RUNNING".equals(latest.desiredState())
        || !"RUNNING".equals(latest.observedState())
        || latest.definitionVersionId() == null
        || definition.publishedDefinitionVersionId() == null) {
      return false;
    }
    return !Objects.equals(
        latest.definitionVersionId(), definition.publishedDefinitionVersionId());
  }
}
