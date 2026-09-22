package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.DefinitionDigest;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.RuntimeEnvironmentRef;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition;
import io.yak.ops.business.sync.realtime.domain.SyncDefinitionDigestCalculator;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.DomainMappingState;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.PublicationCandidate;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.PublicationSnapshot;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.StoredVersion;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper.MappingResult;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper.UnsupportedLegacyDefinitionException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility decorator for immutable DefinitionVersion plus the migrated execution store. */
@Primary
@Repository
public class VersioningRealtimeJobStore implements RealtimeJobStore {

  private final RealtimeJobStoreAdapter delegate;
  private final DefinitionVersionRepository definitionVersions;
  private final CdcPipelineSpecCompatibilityMapper compatibilityMapper;

  public VersioningRealtimeJobStore(
      RealtimeJobStoreAdapter delegate,
      DefinitionVersionRepository definitionVersions,
      CdcPipelineSpecCompatibilityMapper compatibilityMapper) {
    this.delegate = delegate;
    this.definitionVersions = definitionVersions;
    this.compatibilityMapper = compatibilityMapper;
  }

  /** Immutable version creation and legacy release projection remain one transaction. */
  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void publish(long id, int expectedDefinitionVersion, String expectedDigest) {
    DefinitionRow current = delegate.lockDefinition(id);
    if (current.draftRevision() != expectedDefinitionVersion
        || !Objects.equals(current.sourceConfigDigest(), expectedDigest)) {
      throw new IllegalStateException("任务状态或定义版本已变化，请刷新后重新校验并发布");
    }
    if (current.spec() == null) {
      throw new IllegalStateException("实时同步任务没有可发布的 Draft Definition");
    }

    PublicationCandidate candidate = publicationCandidate(current, expectedDigest);
    delegate.publish(id, expectedDefinitionVersion, expectedDigest);
    StoredVersion version = definitionVersions.findOrCreate(candidate);
    definitionVersions.bindPublishedReference(
        id, version.id(), expectedDefinitionVersion, expectedDigest);
  }

  private PublicationCandidate publicationCandidate(
      DefinitionRow current, String sourceConfigDigest) {
    try {
      MappingResult mapped = compatibilityMapper.toDomain(current.spec());
      SyncDefinition definition = mapped.definition();
      DefinitionDigest digest =
          SyncDefinitionDigestCalculator.calculate(
              definition, new RuntimeEnvironmentRef(current.runtimeEnvironmentId()));
      return new PublicationCandidate(
          current.id(),
          current.draftRevision(),
          current.runtimeEnvironmentId(),
          current.spec(),
          sourceConfigDigest,
          definition,
          digest,
          DomainMappingState.MAPPED);
    } catch (UnsupportedLegacyDefinitionException exception) {
      return new PublicationCandidate(
          current.id(),
          current.draftRevision(),
          current.runtimeEnvironmentId(),
          current.spec(),
          sourceConfigDigest,
          null,
          null,
          DomainMappingState.LEGACY_UNMAPPED);
    }
  }

  @Override
  public Optional<PublishedDefinitionRow> publishedDefinition(long definitionId) {
    Optional<Long> ref = definitionVersions.publishedDefinitionVersionId(definitionId);
    if (ref.isEmpty()) return Optional.empty();
    return Optional.of(
        definitionVersion(definitionId, ref.get())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "实时同步任务引用的 Published DefinitionVersion 不存在：" + ref.get())));
  }

  @Override
  public Optional<PublishedDefinitionRow> definitionVersion(long taskId, long definitionVersionId) {
    PublicationSnapshot snapshot = definitionVersions.find(definitionVersionId).orElse(null);
    if (snapshot == null) return Optional.empty();
    StoredVersion version = snapshot.version();
    if (version.taskId() != taskId) {
      throw new IllegalStateException("DefinitionVersion 不属于当前实时同步任务");
    }
    return Optional.of(
        new PublishedDefinitionRow(
            version.id(),
            version.taskId(),
            version.versionNo(),
            version.sourceDraftRevision(),
            snapshot.compatibilityDefinition(),
            snapshot.runtimeEnvironmentId(),
            version.sourceConfigDigest(),
            version.definitionDigest()));
  }

  @Override
  public long insertDefinition(
      String name,
      String description,
      CdcPipelineSpec spec,
      String digest,
      long runtimeEnvironmentId) {
    return delegate.insertDefinition(name, description, spec, digest, runtimeEnvironmentId);
  }

  @Override
  public void updateDefinition(
      long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      String digest,
      long runtimeEnvironmentId) {
    delegate.updateDefinition(id, name, description, spec, digest, runtimeEnvironmentId);
  }

  @Override
  public Optional<DefinitionRow> definition(long id) {
    return delegate.definition(id);
  }

  @Override
  public DefinitionRow lockDefinition(long id) {
    return delegate.lockDefinition(id);
  }

  @Override
  public RealtimeJobPage page(int pageNo, int pageSize, String keyword) {
    return delegate.page(pageNo, pageSize, keyword);
  }

  @Override
  public Optional<DeploymentRow> deploymentByIdempotencyKey(String key) {
    return delegate.deploymentByIdempotencyKey(key);
  }

  @Override
  public Optional<DeploymentRow> latestDeployment(long definitionId) {
    return delegate.latestDeployment(definitionId);
  }

  @Override
  public Optional<ComputeEnvironmentSnapshot> deploymentEnvironment(long deploymentId) {
    return delegate.deploymentEnvironment(deploymentId);
  }

  @Override
  public long insertDeployment(
      DefinitionRow definition,
      CdcPipelineSpec spec,
      String summary,
      String artifactDigest,
      ComputeEnvironmentSnapshot environment,
      String idempotencyKey) {
    return delegate.insertDeployment(
        definition, spec, summary, artifactDigest, environment, idempotencyKey);
  }

  @Override
  public void bindDeploymentDefinitionVersion(
      long deploymentId, long definitionVersionId, int sourceDraftRevision) {
    delegate.bindDeploymentDefinitionVersion(
        deploymentId, definitionVersionId, sourceDraftRevision);
  }

  @Override
  public void markDeploymentRunning(
      long definitionId,
      long deploymentId,
      String engineJobId,
      String runtimeRevision) {
    delegate.markDeploymentRunning(
        definitionId, deploymentId, engineJobId, runtimeRevision);
  }

  @Override
  public void bindDeploymentForStop(
      long deploymentId, String engineJobId, String runtimeRevision) {
    delegate.bindDeploymentForStop(deploymentId, engineJobId, runtimeRevision);
  }

  @Override
  public void markDeployFailure(
      long definitionId,
      long deploymentId,
      boolean uncertain,
      boolean stopRequested,
      String message) {
    delegate.markDeployFailure(
        definitionId, deploymentId, uncertain, stopRequested, message);
  }

  @Override
  public void markStopping(long definitionId, Long deploymentId) {
    delegate.markStopping(definitionId, deploymentId);
  }

  @Override
  public void reserveReplacementStop(
      long definitionId,
      long deploymentId,
      String commandType,
      long targetDefinitionVersionId,
      String idempotencyKey) {
    delegate.reserveReplacementStop(
        definitionId,
        deploymentId,
        commandType,
        targetDefinitionVersionId,
        idempotencyKey);
  }

  @Override
  public void clearReplacementIntent(long deploymentId, String idempotencyKey) {
    delegate.clearReplacementIntent(deploymentId, idempotencyKey);
  }

  @Override
  public void reconcile(
      long definitionId,
      Long deploymentId,
      String observedState,
      String deploymentState,
      String engineJobId,
      String error) {
    delegate.reconcile(
        definitionId, deploymentId, observedState, deploymentState, engineJobId, error);
  }

  @Override
  public void markTerminalFailure(long definitionId, Long deploymentId, String message) {
    delegate.markTerminalFailure(definitionId, deploymentId, message);
  }

  @Override
  public List<DeploymentRow> reconcileCandidates() {
    return delegate.reconcileCandidates();
  }

  @Override
  public void delete(long id) {
    delegate.delete(id);
  }

  @Override
  public void event(
      long definitionId,
      Long deploymentId,
      String type,
      String from,
      String to,
      String message) {
    delegate.event(definitionId, deploymentId, type, from, to, message);
  }

  @Override
  public boolean tryAcquireReconcileLease(String owner, int leaseSeconds) {
    return delegate.tryAcquireReconcileLease(owner, leaseSeconds);
  }

  @Override
  public List<RealtimeJobEventView> events(long definitionId) {
    return delegate.events(definitionId);
  }

  @Override
  public RealtimeJobView view(long id) {
    return delegate.view(id);
  }

  @Override
  public RealtimeJobView.Deployment deploymentView(DeploymentRow deployment) {
    return delegate.deploymentView(deployment);
  }
}
