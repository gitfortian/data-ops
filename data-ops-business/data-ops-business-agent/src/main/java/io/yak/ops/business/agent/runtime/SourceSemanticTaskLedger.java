package io.yak.ops.business.agent.runtime;

import io.agentscope.core.state.AgentStateStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * SDK CAS-backed bounded task coordinator. No Spring component or API is wired until F-039
 * source ownership, plan document checks, project auth and end-to-end dispatch are approved.
 * All reservations commit BEFORE an Agent turn/model/tool call may be launched.
 */
public final class SourceSemanticTaskLedger {
  static final String KEY = "f039_source_task_v1";
  private static final int MAX_ATTEMPTS = 6;
  private final AgentStateStore store;

  public SourceSemanticTaskLedger(AgentStateStore store) {
    this.store = Objects.requireNonNull(store);
    // SDK non-CAS implementations silently overwrite on saveIfVersion: never use them.
    if (!store.supportsVersioning()) throw new IllegalStateException("[F039_CAS_REQUIRED]");
  }

  /** Legacy unbound task snapshots remain readable, but never enter the execution bridge. */
  public SourceSemanticTaskState create(String taskId, String ownerId, SourceSemanticScope scope,
      int tablesPerChunk, int columnsPerChunk, String planSha256,
      long maxTurns, long maxToolCalls) {
    return createInternal(taskId, ownerId, null, scope, tablesPerChunk, columnsPerChunk,
        planSha256, maxTurns, maxToolCalls);
  }

  /** New task admission MUST carry the immutable original session binding. */
  public SourceSemanticTaskState create(String taskId, String ownerId, String sessionId,
      SourceSemanticScope scope, int tablesPerChunk, int columnsPerChunk,
      String planSha256, long maxTurns, long maxToolCalls) {
    SourceSemanticScope.required(sessionId, "sessionId");
    return createInternal(taskId, ownerId, sessionId, scope, tablesPerChunk,
        columnsPerChunk, planSha256, maxTurns, maxToolCalls);
  }

  private SourceSemanticTaskState createInternal(String taskId, String ownerId, String sessionId,
      SourceSemanticScope scope, int tablesPerChunk, int columnsPerChunk,
      String planSha256, long maxTurns, long maxToolCalls) {
    SourceSemanticScope.required(taskId, "taskId");
    SourceSemanticScope.required(ownerId, "ownerId");
    SourceSemanticTaskState.digest(planSha256, "planSha256");
    Objects.requireNonNull(scope);
    // Construct slices only from the canonical authorized scope, never from model input.
    List<SourceSemanticChunkPlanner.Chunk> chunks =
        SourceSemanticChunkPlanner.plan(scope, tablesPerChunk, columnsPerChunk);
    if (chunks.isEmpty() || chunks.size() > 500)
      throw new IllegalArgumentException("invalid chunk plan");
    List<String> ids = chunks.stream().map(SourceSemanticChunkPlanner.Chunk::id).toList();
    var state = new SourceSemanticTaskState(taskId, scope.projectId(), ownerId,
        scope.fingerprint(), SourceSemanticChunkPlanner.planFingerprint(scope, chunks),
        planSha256, SourceSemanticTaskState.Status.PLANNED, ids, List.of(), java.util.Map.of(),
        java.util.Map.of(), null, null, maxTurns, 0, maxToolCalls, 0, 1, sessionId,
        scope, chunks);
    try {
      long v = store.saveIfVersion(ownerId, taskSlot(taskId), KEY, state, 0);
      if (v == AgentStateStore.UNVERSIONED)
        throw new IllegalStateException("[F039_TASK_ALREADY_EXISTS]");
      return state;
    } catch (IllegalStateException e) {
      throw e;
    } catch (RuntimeException storageFailure) {
      throw new IllegalStateException("[F039_LEDGER_UNAVAILABLE]", storageFailure);
    }
  }

  public SourceSemanticTaskState read(String ownerId, long projectId, String taskId) {
    return load(ownerId, projectId, taskId).value();
  }

  public SourceSemanticTaskState confirmPlan(String ownerId, long projectId, String taskId,
      String scopeFingerprint, String planSha256) {
    return change(ownerId, projectId, taskId, old -> {
      verifyScope(old, scopeFingerprint, planSha256);
      if (old.status() != SourceSemanticTaskState.Status.PLANNED)
        throw new IllegalStateException("[F039_PLAN_NOT_EDITABLE]");
      return copy(old, SourceSemanticTaskState.Status.READY, old.completedChunkIds(),
          old.resultDigests(), old.completedTurnIds(), null, null,
          old.usedTurns(), old.reservedToolCalls());
    });
  }

  /** Reserve an entire bounded turn/tool allowance before dispatch. No refund on failure. */
  public SourceSemanticTaskState reserveTurn(String ownerId, long projectId, String taskId,
      String scopeFingerprint, String planSha256, String chunkId, String turnId, int toolCalls) {
    SourceSemanticScope.required(turnId, "turnId");
    return change(ownerId, projectId, taskId, old -> {
      verifyScope(old, scopeFingerprint, planSha256);
      if (old.status() != SourceSemanticTaskState.Status.READY
          || !Objects.equals(old.nextChunkId(), chunkId))
        throw new IllegalStateException("[F039_CHUNK_NOT_READY]");
      if (toolCalls <= 0 || toolCalls > old.maxToolCalls() - old.reservedToolCalls()
          || old.usedTurns() >= old.maxTurns())
        throw new IllegalStateException("[F039_TASK_BUDGET_EXHAUSTED]");
      return copy(old, SourceSemanticTaskState.Status.RUNNING, old.completedChunkIds(),
          old.resultDigests(), old.completedTurnIds(), chunkId, turnId,
          old.usedTurns() + 1, old.reservedToolCalls() + toolCalls);
    });
  }

  /** Register an immutable result digest linked to a real, separately verified AgentTurn. */
  public SourceSemanticTaskState completeTurn(String ownerId, long projectId, String taskId,
      String scopeFingerprint, String planSha256, String chunkId, String turnId,
      String resultSha256) {
    SourceSemanticTaskState.digest(resultSha256, "resultSha256");
    return change(ownerId, projectId, taskId, old -> {
      verifyScope(old, scopeFingerprint, planSha256);
      verifyActive(old, chunkId, turnId);
      var completed = new ArrayList<>(old.completedChunkIds());
      completed.add(chunkId);
      var results = new HashMap<>(old.resultDigests());
      results.put(chunkId, resultSha256);
      var turns = new HashMap<>(old.completedTurnIds());
      turns.put(chunkId, turnId);
      SourceSemanticTaskState.Status next = completed.size() == old.chunkIds().size()
          ? SourceSemanticTaskState.Status.COMPLETED
          : old.status() == SourceSemanticTaskState.Status.PAUSE_REQUESTED
              ? SourceSemanticTaskState.Status.PAUSED
              : SourceSemanticTaskState.Status.READY;
      return copy(old, next, completed, results, turns, null, null,
          old.usedTurns(), old.reservedToolCalls());
    });
  }

  /** Crash, stopped turn or failed LLM is not a completed chunk; next turn is explicit. */
  public SourceSemanticTaskState interrupt(String ownerId, long projectId, String taskId,
      String activeTurnId) {
    return change(ownerId, projectId, taskId, old -> {
      verifyActive(old, old.activeChunkId(), activeTurnId);
      return copy(old, SourceSemanticTaskState.Status.INTERRUPTED,
          old.completedChunkIds(), old.resultDigests(), old.completedTurnIds(),
          null, null, old.usedTurns(), old.reservedToolCalls());
    });
  }

  /** Recheck original source and approved plan identity before creating a NEW turn. */
  public SourceSemanticTaskState resume(String ownerId, long projectId, String taskId,
      String scopeFingerprint, String planSha256) {
    return change(ownerId, projectId, taskId, old -> {
      verifyScope(old, scopeFingerprint, planSha256);
      if (old.status() != SourceSemanticTaskState.Status.PAUSED
          && old.status() != SourceSemanticTaskState.Status.INTERRUPTED)
        throw new IllegalStateException("[F039_TASK_NOT_RESUMABLE]");
      return copy(old, SourceSemanticTaskState.Status.READY, old.completedChunkIds(),
          old.resultDigests(), old.completedTurnIds(), null, null,
          old.usedTurns(), old.reservedToolCalls());
    });
  }

  public SourceSemanticTaskState pause(String ownerId, long projectId, String taskId) {
    return change(ownerId, projectId, taskId, old -> {
      if (old.status() == SourceSemanticTaskState.Status.RUNNING) {
        // Pause *after* the current original turn: never pretend model inference paused.
        return copy(old, SourceSemanticTaskState.Status.PAUSE_REQUESTED,
            old.completedChunkIds(), old.resultDigests(), old.completedTurnIds(),
            old.activeChunkId(), old.activeTurnId(),
            old.usedTurns(), old.reservedToolCalls());
      }
      if (old.status() != SourceSemanticTaskState.Status.READY)
        throw new IllegalStateException("[F039_TASK_NOT_PAUSABLE]");
      return copy(old, SourceSemanticTaskState.Status.PAUSED, old.completedChunkIds(),
          old.resultDigests(), old.completedTurnIds(), null, null,
          old.usedTurns(), old.reservedToolCalls());
    });
  }

  /** Marks auxiliary task cancelled. Caller MUST still stop real turn via original Registry. */
  public SourceSemanticTaskState cancel(String ownerId, long projectId, String taskId) {
    return change(ownerId, projectId, taskId, old -> {
      if (old.status() == SourceSemanticTaskState.Status.COMPLETED
          || old.status() == SourceSemanticTaskState.Status.CANCELLED)
        throw new IllegalStateException("[F039_TASK_TERMINAL]");
      return copy(old, SourceSemanticTaskState.Status.CANCELLED,
          old.completedChunkIds(), old.resultDigests(), old.completedTurnIds(),
          null, null, old.usedTurns(), old.reservedToolCalls());
    });
  }

  private SourceSemanticTaskState change(String ownerId, long projectId, String taskId,
      Function<SourceSemanticTaskState, SourceSemanticTaskState> transition) {
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      var snapshot = load(ownerId, projectId, taskId);
      var next = transition.apply(snapshot.value());
      try {
        long updated = store.saveIfVersion(ownerId, taskSlot(taskId), KEY,
            next, snapshot.version());
        if (updated != AgentStateStore.UNVERSIONED) return next;
      } catch (RuntimeException storageFailure) {
        throw new IllegalStateException("[F039_LEDGER_UNAVAILABLE]", storageFailure);
      }
    }
    throw new IllegalStateException("[F039_TASK_CAS_CONFLICT]");
  }

  private Snapshot load(String ownerId, long projectId, String taskId) {
    SourceSemanticScope.required(ownerId, "ownerId");
    String slot = taskSlot(taskId);
    try {
      var versioned = store.getVersioned(ownerId, slot, KEY, SourceSemanticTaskState.class);
      SourceSemanticTaskState value = versioned.value();
      if (value == null) throw new IllegalArgumentException("[F039_TASK_NOT_FOUND]");
      if (versioned.version() == AgentStateStore.UNVERSIONED)
        throw new IllegalStateException("[F039_CAS_REQUIRED]");
      if (value.projectId() != projectId || !value.ownerId().equals(ownerId)
          || !value.taskId().equals(taskId))
        throw new IllegalArgumentException("[F039_TASK_SCOPE_MISMATCH]");
      return new Snapshot(value, versioned.version());
    } catch (IllegalArgumentException | IllegalStateException known) {
      throw known;
    } catch (RuntimeException unavailable) {
      throw new IllegalStateException("[F039_LEDGER_UNAVAILABLE]", unavailable);
    }
  }

  private static String taskSlot(String taskId) {
    return "f039_" + SourceSemanticScope.required(taskId, "taskId");
  }

  private static void verifyScope(SourceSemanticTaskState state, String scope, String plan) {
    if (!Objects.equals(state.scopeFingerprint(), scope)
        || !Objects.equals(state.planSha256(), plan))
      throw new IllegalStateException("[F039_SOURCE_OR_PLAN_DRIFT]");
  }

  private static void verifyActive(SourceSemanticTaskState state, String chunkId, String turnId) {
    if ((state.status() != SourceSemanticTaskState.Status.RUNNING
            && state.status() != SourceSemanticTaskState.Status.PAUSE_REQUESTED)
        || !Objects.equals(state.activeChunkId(), chunkId)
        || !Objects.equals(state.activeTurnId(), turnId))
      throw new IllegalStateException("[F039_STALE_TURN]");
  }

  private static SourceSemanticTaskState copy(SourceSemanticTaskState old,
      SourceSemanticTaskState.Status status, List<String> completed,
      java.util.Map<String, String> results, java.util.Map<String, String> turns,
      String activeChunkId, String activeTurnId, long usedTurns, long usedToolCalls) {
    return new SourceSemanticTaskState(old.taskId(), old.projectId(), old.ownerId(),
        old.scopeFingerprint(), old.chunkPlanFingerprint(), old.planSha256(),
        status, old.chunkIds(), completed, results, turns, activeChunkId, activeTurnId,
        old.maxTurns(), usedTurns, old.maxToolCalls(), usedToolCalls, old.revision() + 1,
        old.sessionId(), old.sourceManifest(), old.frozenChunks());
  }

  private record Snapshot(SourceSemanticTaskState value, long version) {}
}
