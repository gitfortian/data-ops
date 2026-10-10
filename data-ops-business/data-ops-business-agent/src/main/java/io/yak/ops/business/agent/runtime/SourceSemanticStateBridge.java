package io.yak.ops.business.agent.runtime;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import java.util.Objects;

/**
 * The ONLY SDK StateStore/State corridor for F-039 business facades. Protects both
 * immutable, source-bound result snapshots and the CAS task ledger without allowing
 * conversation/controller to import the SDK persistence API.
 */
public final class SourceSemanticStateBridge {
  private static final String ARTIFACT_KEY = "f039_immutable_artifact_v1";
  private final AgentStateStore store;
  private final SourceSemanticTaskLedger ledger;
  private final SourceSemanticCandidateLedger candidates;

  public record Artifact(String taskId, String chunkId, String turnId, String markdown,
      String sha256, String scopeFingerprint, String planSha256) implements State {}

  public SourceSemanticStateBridge(AgentStateStore store) {
    this.store = Objects.requireNonNull(store);
    this.ledger = new SourceSemanticTaskLedger(store);
    this.candidates = new SourceSemanticCandidateLedger(store);
  }

  public SourceSemanticTaskLedger ledger() { return ledger; }
  public SourceSemanticCandidateLedger candidates() { return candidates; }

  public Artifact readArtifact(String ownerId, String taskId, String chunkId) {
    return store.getVersioned(ownerId, artifactSlot(taskId, chunkId),
        ARTIFACT_KEY, Artifact.class).value();
  }

  /** Save-if-absent CAS; a second write may only observe exact immutable equality. */
  public Artifact saveImmutable(String ownerId, Artifact artifact) {
    Objects.requireNonNull(artifact);
    long version;
    try {
      version = store.saveIfVersion(ownerId, artifactSlot(artifact.taskId(), artifact.chunkId()),
          ARTIFACT_KEY, artifact, 0);
    } catch (RuntimeException failure) {
      throw new IllegalStateException("[F039_ARTIFACT_STORE_UNAVAILABLE]", failure);
    }
    Artifact saved = version == AgentStateStore.UNVERSIONED
        ? readArtifact(ownerId, artifact.taskId(), artifact.chunkId()) : artifact;
    if (!artifact.equals(saved))
      throw new IllegalStateException("[F039_IMMUTABLE_ARTIFACT_CONFLICT]");
    return saved;
  }

  private static String artifactSlot(String taskId, String chunkId) {
    return "f039_artifact_" + taskId + "_" + chunkId;
  }
}
