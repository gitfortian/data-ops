package io.yak.ops.business.sync.realtime.dao;

import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobEventPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobListRow;
import java.util.List;
import java.util.Optional;

/** Realtime Sync persistence boundary. Ordinary business methods are Project-required. */
public interface RealtimeJobDao {
  long insertDefinition(RealtimeJobDefinitionPO definition);
  int updateDefinition(long id, String name, String description, String specJson, String digest, long environmentId);
  int publish(long id, int expectedDefinitionVersion, String expectedDigest);
  Optional<RealtimeJobDefinitionPO> findDefinition(long id);
  Optional<RealtimeJobDefinitionPO> lockDefinition(long id);
  Optional<RealtimeJobDeploymentPO> deploymentByIdempotencyKey(String key);
  Optional<RealtimeJobDeploymentPO> latestDeployment(long definitionId);
  Optional<RealtimeJobDeploymentPO> findDeployment(long deploymentId);
  long insertDeployment(RealtimeJobDeploymentPO deployment);
  void bindDeploymentDefinitionVersion(long deploymentId, long definitionVersionId, int sourceDraftRevision);
  int markDeploymentRunning(long definitionId, long deploymentId, String engineJobId, String runtimeRevision);
  void bindDeploymentForStop(long deploymentId, String engineJobId, String runtimeRevision);
  void markDeployFailure(long definitionId, long deploymentId, boolean uncertain, boolean stopRequested, String message);
  void markStopping(long definitionId, Long deploymentId);
  void reserveReplacementStop(long definitionId, long deploymentId, String commandType, long targetDefinitionVersionId, String idempotencyKey);
  void clearReplacementIntent(long deploymentId, String idempotencyKey);
  void reconcile(long definitionId, Long deploymentId, String observedState, String deploymentState, String engineJobId, String error);
  void markTerminalFailure(long definitionId, Long deploymentId, String message);

  /** Project-scoped candidate lookup for callers already running inside ProjectContext. */
  List<RealtimeJobDeploymentPO> reconcileExecutions();

  /** Explicit cross-Project dispatcher scan. Returns durable identity only. */
  List<ProjectDeploymentRef> findReconcileCandidatesForDispatch();

  int deleteDefinition(long id);
  void insertEvent(RealtimeJobEventPO event);

  /** Runtime lease is platform-global and deliberately does not require ProjectContext. */
  boolean tryAcquireReconcileLease(String owner, int leaseSeconds);

  List<RealtimeJobEventPO> events(long definitionId);
  int bindRuntimeIdentity(String idempotencyKey, String runtimeJobName);
  Optional<String> runtimeJobName(long deploymentId);
  long countPage(String keyword, Long id, String releaseState, String stateGroup);
  List<RealtimeJobListRow> page(String keyword, Long id, String releaseState, String stateGroup, int limit, int offset);

  record ProjectDeploymentRef(long projectId, long definitionId, long deploymentId) {
    public ProjectDeploymentRef {
      if (projectId <= 0L) throw new IllegalArgumentException("ProjectId 必须大于 0");
      if (definitionId <= 0L) throw new IllegalArgumentException("DefinitionId 必须大于 0");
      if (deploymentId <= 0L) throw new IllegalArgumentException("DeploymentId 必须大于 0");
    }
  }
}
