package io.yak.ops.business.agent.runtime;

import io.agentscope.core.state.State;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded F-039 task auxiliary ledger. The existing AgentTurn/StateStore own turn execution,
 * messages and pending confirmations. This record owns only cross-turn reservations and links.
 */
public record SourceSemanticTaskState(
    String taskId, long projectId, String ownerId, String scopeFingerprint,
    String chunkPlanFingerprint, String planSha256, Status status,
    List<String> chunkIds, List<String> completedChunkIds,
    Map<String, String> resultDigests, Map<String, String> completedTurnIds,
    String activeChunkId, String activeTurnId,
    long maxTurns, long usedTurns, long maxToolCalls, long reservedToolCalls,
    long revision, String sessionId, SourceSemanticScope sourceManifest,
    List<SourceSemanticChunkPlanner.Chunk> frozenChunks) implements State {

  public enum Status { PLANNED, READY, RUNNING, PAUSE_REQUESTED, PAUSED, INTERRUPTED, COMPLETED, CANCELLED }

  public SourceSemanticTaskState {
    SourceSemanticScope.required(taskId, "taskId");
    SourceSemanticScope.required(ownerId, "ownerId");
    // Older #500 task snapshots have no session binding. Never infer it from a turn.
    if (sessionId != null) SourceSemanticScope.required(sessionId, "sessionId");
    // Old #500/#502 records may lack these new fields. They stay readable, but cannot
    // be used as a trusted source manifest for fresh turn admission.
    if ((sourceManifest == null) != (frozenChunks == null)) {
      throw new IllegalArgumentException("[F039_PARTIAL_SOURCE_MANIFEST]");
    }
    if (sourceManifest != null) {
      frozenChunks = List.copyOf(frozenChunks);
      if (sourceManifest.projectId() != projectId
          || !sourceManifest.fingerprint().equals(scopeFingerprint)
          || !frozenChunks.stream().map(SourceSemanticChunkPlanner.Chunk::id).toList()
              .equals(chunkIds)
          || !SourceSemanticChunkPlanner.planFingerprint(sourceManifest, frozenChunks)
              .equals(chunkPlanFingerprint)) {
        throw new IllegalArgumentException("[F039_SOURCE_MANIFEST_MISMATCH]");
      }
    }
    if (projectId <= 0) throw new IllegalArgumentException("projectId");
    digest(scopeFingerprint, "scopeFingerprint");
    digest(chunkPlanFingerprint, "chunkPlanFingerprint");
    digest(planSha256, "planSha256");
    Objects.requireNonNull(status);
    chunkIds = List.copyOf(chunkIds);
    completedChunkIds = List.copyOf(completedChunkIds);
    resultDigests = Map.copyOf(resultDigests);
    completedTurnIds = Map.copyOf(completedTurnIds);
    if (chunkIds.isEmpty() || chunkIds.size() > 500
        || new HashSet<>(chunkIds).size() != chunkIds.size()
        || new HashSet<>(completedChunkIds).size() != completedChunkIds.size()
        || !new HashSet<>(chunkIds).containsAll(completedChunkIds)
        || !resultDigests.keySet().equals(new HashSet<>(completedChunkIds))
        || !completedTurnIds.keySet().equals(new HashSet<>(completedChunkIds))) {
      throw new IllegalArgumentException("invalid bounded chunk completion ledger");
    }
    chunkIds.forEach(id -> digest(id, "chunkId"));
    resultDigests.values().forEach(id -> digest(id, "resultDigest"));
    if (maxTurns < 1 || maxTurns > 512 || usedTurns < 0 || usedTurns > maxTurns
        || maxToolCalls < 1 || maxToolCalls > 20000 || reservedToolCalls < 0
        || reservedToolCalls > maxToolCalls || revision < 1)
      throw new IllegalArgumentException("invalid cumulative task reservation");
    if ((activeTurnId == null) != (activeChunkId == null)
        || (status == Status.RUNNING || status == Status.PAUSE_REQUESTED)
            != (activeTurnId != null))
      throw new IllegalArgumentException("active turn must belong only to RUNNING");
    if (activeChunkId != null && (!chunkIds.contains(activeChunkId)
        || completedChunkIds.contains(activeChunkId)))
      throw new IllegalArgumentException("invalid active chunk");
    if (status == Status.COMPLETED && completedChunkIds.size() != chunkIds.size())
      throw new IllegalArgumentException("incomplete task cannot be COMPLETED");
  }

  static void digest(String value, String field) {
    if (value == null || !value.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("invalid " + field);
  }

  public String nextChunkId() {
    for (String id : chunkIds) if (!completedChunkIds.contains(id)) return id;
    return null;
  }
}
