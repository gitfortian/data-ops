package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecution.EngineExecutionRef;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistence facade for the migrated realtime model.
 *
 * <p>The physical schema still uses legacy "job/definition/deployment" names, but application
 * lifecycle truth is DefinitionVersion + SyncExecution. Task-row runtime columns are not part of
 * this contract anymore.
 */
public interface RealtimeJobStore {
  long insertDefinition(String name, String description, CdcPipelineSpec spec, String digest, long runtimeEnvironmentId);
  void updateDefinition(long id, String name, String description, CdcPipelineSpec spec, String digest, long runtimeEnvironmentId);
  void publish(long id, int expectedDefinitionVersion, String expectedDigest);
  Optional<DefinitionRow> definition(long id);
  default long runtimeEnvironmentId(long definitionId) {
    return definition(definitionId)
        .map(DefinitionRow::runtimeEnvironmentId)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + definitionId));
  }
  DefinitionRow lockDefinition(long id);
  RealtimeJobPage page(int pageNo, int pageSize, String keyword);
  default Optional<PublishedDefinitionRow> publishedDefinition(long definitionId) {
    return Optional.empty();
  }
  /** Lookup one explicit immutable DefinitionVersion. */
  default Optional<PublishedDefinitionRow> definitionVersion(long taskId, long definitionVersionId) {
    return Optional.empty();
  }
  Optional<DeploymentRow> deploymentByIdempotencyKey(String key);
  Optional<DeploymentRow> latestDeployment(long definitionId);
  default Optional<SyncExecution> latestExecution(long definitionId) {
    return latestDeployment(definitionId).map(DeploymentRow::execution);
  }
  default Optional<ComputeEnvironmentSnapshot> deploymentEnvironment(long deploymentId) {
    return Optional.empty();
  }
  long insertDeployment(DefinitionRow definition, CdcPipelineSpec spec, String summary, String artifactDigest, ComputeEnvironmentSnapshot environment, String idempotencyKey);
  default void bindDeploymentDefinitionVersion(
      long deploymentId, long definitionVersionId, int sourceDraftRevision) {
    throw new UnsupportedOperationException("当前 RealtimeJobStore 不支持 DefinitionVersion 绑定");
  }
  void markDeploymentRunning(long definitionId, long deploymentId, String engineJobId, String runtimeRevision);
  void bindDeploymentForStop(long deploymentId, String engineJobId, String runtimeRevision);
  void markDeployFailure(long definitionId, long deploymentId, boolean uncertain, boolean stopRequested, String message);
  void markStopping(long definitionId, Long deploymentId);
  void reserveReplacementStop(
      long definitionId,
      long deploymentId,
      String commandType,
      long targetDefinitionVersionId,
      String idempotencyKey);
  void clearReplacementIntent(long deploymentId, String idempotencyKey);
  void reconcile(long definitionId, Long deploymentId, String observedState, String deploymentState, String engineJobId, String error);
  void markTerminalFailure(long definitionId, Long deploymentId, String message);

  /** Latest active/uncertain executions that require authoritative runtime reconciliation. */
  default List<DeploymentRow> reconcileCandidates() {
    return List.of();
  }

  void delete(long id);
  void event(long definitionId, Long deploymentId, String type, String from, String to, String message);
  boolean tryAcquireReconcileLease(String owner, int leaseSeconds);
  List<RealtimeJobEventView> events(long definitionId);
  RealtimeJobView view(long id);
  default CdcPipelineSpec spec(DefinitionRow definition) { return definition.spec(); }
  RealtimeJobView.Deployment deploymentView(DeploymentRow deployment);

  record PublishedDefinitionRow(
      long id,
      long taskId,
      int versionNo,
      int sourceDraftRevision,
      CdcPipelineSpec spec,
      long runtimeEnvironmentId,
      String sourceConfigDigest,
      String definitionDigest) {
    public PublishedDefinitionRow {
      if (id <= 0) throw new IllegalArgumentException("DefinitionVersionId 必须大于 0");
      if (taskId <= 0) throw new IllegalArgumentException("TaskId 必须大于 0");
      if (versionNo <= 0) throw new IllegalArgumentException("Published versionNo 必须大于 0");
      if (sourceDraftRevision <= 0) {
        throw new IllegalArgumentException("Published sourceDraftRevision 必须大于 0");
      }
      if (spec == null) throw new IllegalArgumentException("Published definition snapshot 不能为空");
      if (runtimeEnvironmentId <= 0) {
        throw new IllegalArgumentException("Published RuntimeEnvironmentRef 必须大于 0");
      }
    }
  }

  /**
   * Current RealtimeSyncTask + mutable Draft persistence row.
   *
   * <p>desiredState/observedState/lastError remain physical compatibility values only. New
   * application code MUST use latest SyncExecution instead.
   */
  record DefinitionRow(
      long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      long runtimeEnvironmentId,
      String releaseState,
      String desiredState,
      String observedState,
      int definitionVersion,
      Integer publishedVersion,
      Long publishedDefinitionVersionId,
      String configDigest,
      String lastError,
      LocalDateTime createTime,
      LocalDateTime updateTime) {

    /** Semantic name for the mutable Draft revision. */
    public int draftRevision() {
      return definitionVersion;
    }

    /** Legacy marker only; not DefinitionVersion.versionNo. */
    public Integer publishedDraftRevision() {
      return publishedVersion;
    }

    /** Exact compatibility digest used for optimistic Draft/Publish CAS. */
    public String sourceConfigDigest() {
      return configDigest;
    }

    /** Compatibility constructor retained only for older tests/adapters during contract cleanup. */
    public DefinitionRow(
        long id,
        String name,
        String description,
        CdcPipelineSpec spec,
        long runtimeEnvironmentId,
        String releaseState,
        String desiredState,
        String observedState,
        int definitionVersion,
        Integer publishedVersion,
        String configDigest,
        String lastError,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
      this(
          id,
          name,
          description,
          spec,
          runtimeEnvironmentId,
          releaseState,
          desiredState,
          observedState,
          definitionVersion,
          publishedVersion,
          null,
          configDigest,
          lastError,
          createTime,
          updateTime);
    }
  }

  /**
   * SyncExecution persistence compatibility row.
   *
   * <p>The physical table is still named yak_realtime_job_deployment. desiredState/observedState
   * are authoritative. status/configDigest are compatibility storage names only. Replacement fields
   * persist a RestartExecution/ApplyPublishedVersion intent across the STOPPED -> successor window.
   */
  record DeploymentRow(
      long id,
      long definitionId,
      Long definitionVersionId,
      int definitionVersion,
      CdcPipelineSpec specSnapshot,
      String specSummary,
      String configDigest,
      String idempotencyKey,
      String engineJobId,
      String runtimeRevision,
      ComputeEnvironmentSnapshot runtimeEnvironment,
      String engineType,
      String desiredState,
      String observedState,
      String status,
      boolean resultUncertain,
      String errorMessage,
      String replacementCommandType,
      Long replacementTargetDefinitionVersionId,
      String replacementIdempotencyKey,
      LocalDateTime createTime,
      LocalDateTime updateTime) {

    public DeploymentRow {
      engineType = hasText(engineType) ? engineType.trim() : "FLINK_CDC";
      desiredState = hasText(desiredState) ? desiredState.trim() : legacyDesiredState(status);
      observedState = hasText(observedState) ? observedState.trim() : legacyObservedState(status);
      replacementCommandType =
          hasText(replacementCommandType) ? replacementCommandType.trim() : null;
      replacementIdempotencyKey =
          hasText(replacementIdempotencyKey) ? replacementIdempotencyKey.trim() : null;
    }

    /** Legacy source DraftRevision evidence; not immutable DefinitionVersionId. */
    public int sourceDraftRevision() {
      return definitionVersion;
    }

    /** Digest of the compiled execution artifact; not DefinitionDigest. */
    public String artifactDigest() {
      return configDigest;
    }

    public boolean replacementPending() {
      return replacementCommandType != null
          && replacementTargetDefinitionVersionId != null
          && replacementIdempotencyKey != null;
    }

    public boolean replacementMatches(
        String commandType, long targetDefinitionVersionId, String key) {
      return replacementPending()
          && Objects.equals(replacementCommandType, commandType)
          && replacementTargetDefinitionVersionId == targetDefinitionVersionId
          && Objects.equals(replacementIdempotencyKey, key);
    }

    public SyncExecution execution() {
      return new SyncExecution(
          id,
          definitionId,
          definitionVersionId,
          DesiredState.valueOf(desiredState),
          ObservedState.valueOf(observedState),
          new EngineExecutionRef(engineType, engineJobId),
          resultUncertain,
          errorMessage);
    }

    /** Constructor matching the pre-replacement-intent execution row contract. */
    public DeploymentRow(
        long id,
        long definitionId,
        Long definitionVersionId,
        int definitionVersion,
        CdcPipelineSpec specSnapshot,
        String specSummary,
        String configDigest,
        String idempotencyKey,
        String engineJobId,
        String runtimeRevision,
        ComputeEnvironmentSnapshot runtimeEnvironment,
        String engineType,
        String desiredState,
        String observedState,
        String status,
        boolean resultUncertain,
        String errorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
      this(
          id,
          definitionId,
          definitionVersionId,
          definitionVersion,
          specSnapshot,
          specSummary,
          configDigest,
          idempotencyKey,
          engineJobId,
          runtimeRevision,
          runtimeEnvironment,
          engineType,
          desiredState,
          observedState,
          status,
          resultUncertain,
          errorMessage,
          null,
          null,
          null,
          createTime,
          updateTime);
    }

    /** Compatibility constructor for persisted rows created before execution lifecycle columns. */
    public DeploymentRow(
        long id,
        long definitionId,
        Long definitionVersionId,
        int definitionVersion,
        CdcPipelineSpec specSnapshot,
        String specSummary,
        String configDigest,
        String idempotencyKey,
        String engineJobId,
        String runtimeRevision,
        ComputeEnvironmentSnapshot runtimeEnvironment,
        String status,
        boolean resultUncertain,
        String errorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
      this(
          id,
          definitionId,
          definitionVersionId,
          definitionVersion,
          specSnapshot,
          specSummary,
          configDigest,
          idempotencyKey,
          engineJobId,
          runtimeRevision,
          runtimeEnvironment,
          "FLINK_CDC",
          legacyDesiredState(status),
          legacyObservedState(status),
          status,
          resultUncertain,
          errorMessage,
          null,
          null,
          null,
          createTime,
          updateTime);
    }

    /** Compatibility constructor for historical rows without DefinitionVersionId. */
    public DeploymentRow(
        long id,
        long definitionId,
        int definitionVersion,
        CdcPipelineSpec specSnapshot,
        String specSummary,
        String configDigest,
        String idempotencyKey,
        String engineJobId,
        String runtimeRevision,
        ComputeEnvironmentSnapshot runtimeEnvironment,
        String status,
        boolean resultUncertain,
        String errorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
      this(
          id,
          definitionId,
          null,
          definitionVersion,
          specSnapshot,
          specSummary,
          configDigest,
          idempotencyKey,
          engineJobId,
          runtimeRevision,
          runtimeEnvironment,
          status,
          resultUncertain,
          errorMessage,
          createTime,
          updateTime);
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }

    private static String legacyDesiredState(String status) {
      return switch (status == null ? "" : status) {
        case "STOPPING", "STOPPED", "FAILED", "REJECTED" -> "STOPPED";
        default -> "RUNNING";
      };
    }

    private static String legacyObservedState(String status) {
      return switch (status == null ? "" : status) {
        case "SUBMITTING" -> "STARTING";
        case "RUNNING" -> "RUNNING";
        case "STOPPING" -> "STOPPING";
        case "STOPPED" -> "STOPPED";
        case "FAILED", "REJECTED" -> "FAILED";
        case "UNKNOWN" -> "UNKNOWN";
        default -> "UNKNOWN";
      };
    }
  }
}
