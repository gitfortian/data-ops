package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;

/**
 * Secret-free realtime task read model.
 *
 * <p>Legacy JSON field names are intentionally preserved for API compatibility. Application and
 * new UI code should use the semantic alias methods below when reasoning about DraftRevision and
 * execution artifacts.
 */
public record RealtimeJobView(
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
    LocalDateTime updateTime,
    boolean publishedUpdateAvailable,
    Deployment latestDeployment) {

  public int draftRevision() {
    return definitionVersion;
  }

  public Integer publishedDraftRevision() {
    return publishedVersion;
  }

  public String sourceConfigDigest() {
    return configDigest;
  }

  /** Compatibility constructor for callers created before Wave 5 added the derived capability. */
  public RealtimeJobView(
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
      LocalDateTime updateTime,
      Deployment latestDeployment) {
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
        configDigest,
        lastError,
        createTime,
        updateTime,
        false,
        latestDeployment);
  }

  /**
   * SyncExecution read projection. The type name stays Deployment because the public v1 JSON shape
   * and existing clients still use latestDeployment; it is not a separate deployment domain model.
   */
  public record Deployment(
      long id,
      int definitionVersion,
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

    public int sourceDraftRevision() {
      return definitionVersion;
    }

    public String artifactDigest() {
      return configDigest;
    }
  }
}
