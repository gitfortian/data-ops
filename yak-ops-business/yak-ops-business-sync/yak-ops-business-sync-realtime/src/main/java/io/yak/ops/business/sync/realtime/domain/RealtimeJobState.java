package io.yak.ops.business.sync.realtime.domain;

/** Orthogonal state axes used by the realtime control plane. */
public final class RealtimeJobState {

  private RealtimeJobState() {}

  public enum ReleaseState {
    DRAFT,
    PUBLISHED
  }

  public enum DesiredState {
    RUNNING,
    STOPPED
  }

  public enum ObservedState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
    UNKNOWN,
    CONFLICT
  }

  public enum DeploymentState {
    SUBMITTING,
    RUNNING,
    STOPPING,
    STOPPED,
    REJECTED,
    FAILED,
    UNKNOWN
  }
}
