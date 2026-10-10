package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.runtime.SourceSemanticPlanDocumentGuard;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskLedger;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskState;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * F-039 read/reconciliation adapter for the original AgentTurn truth.
 *
 * <p>No new worker, status table, Spring service, model tool or public HTTP route.
 * An authorized application adapter must supply a fresh source authorization+fingerprint,
 * a server-trusted workspace, and an independently validated immutable result receipt.
 * The original AgentTurn still owns queue/running/waiting/terminal states.
 */
public final class SourceSemanticOriginalTurnReconciler {
  private final SourceSemanticTaskLedger ledger;
  private final AgentTurnRepository turns;
  private final SourceSemanticPlanDocumentGuard planFiles;
  private final FreshSourceEvidence freshSource;
  private final VerifiedResultReceipts receipts;
  private final ExactTurnStop stop;

  /**
   * This port MUST check effective user datasource ACL, current project and the complete
   * Metadata selection against a persisted server-owned task manifest. No caller-supplied
   * hash, model argument or stale cached snapshot qualifies.
   */
  @FunctionalInterface
  public interface FreshSourceEvidence {
    String reauthorizeAndFingerprint(Access access, SourceSemanticTaskState frozen);
  }

  /** Only a trusted durable artifact owner may implement this. No LLM text or HTTP receipt. */
  @FunctionalInterface
  public interface VerifiedResultReceipts {
    Optional<Receipt> read(AgentTurnRecord completedTurn, String chunkId);
  }

  /** Implementation must reuse the authenticated AgentChatService.cancelTurn(turnId). */
  @FunctionalInterface
  public interface ExactTurnStop {
    void stop(String turnId);
  }

  public record Access(String ownerId, long projectId, String taskId,
      String sessionId, Path trustedWorkspace) {
    public Access {
      if (ownerId == null || ownerId.isBlank() || taskId == null || taskId.isBlank()
          || sessionId == null || sessionId.isBlank() || projectId <= 0
          || trustedWorkspace == null) {
        throw new IllegalArgumentException("[F039_INVALID_TASK_ACCESS]");
      }
    }
  }

  /** Verified receipt is tightly bound to one original turn, chunk and frozen source/plan. */
  public record Receipt(String turnId, String chunkId, String sessionId,
      long projectId, long userId, String sourceFingerprint,
      String planSha256, String resultSha256) {}

  public enum Outcome {
    NO_ACTIVE_TURN, QUEUED, RUNNING, WAITING_INPUT, ORIGINAL_TURN_MISSING,
    RECEIPT_NOT_VERIFIED, CHUNK_VERIFIED, ORIGINAL_TURN_INTERRUPTED
  }

  /** Task status is an auxiliary projection, not a replacement for AgentTurn status. */
  public record Progress(SourceSemanticTaskState task, TurnStatus originalStatus,
      Outcome outcome, String nextChunkId) {}

  public SourceSemanticOriginalTurnReconciler(SourceSemanticTaskLedger ledger,
      AgentTurnRepository turns, SourceSemanticPlanDocumentGuard planFiles,
      FreshSourceEvidence freshSource, VerifiedResultReceipts receipts, ExactTurnStop stop) {
    this.ledger = Objects.requireNonNull(ledger);
    this.turns = Objects.requireNonNull(turns);
    this.planFiles = Objects.requireNonNull(planFiles);
    this.freshSource = Objects.requireNonNull(freshSource);
    this.receipts = Objects.requireNonNull(receipts);
    this.stop = Objects.requireNonNull(stop);
  }

  /**
   * Refresh-safe read. NEVER changes a task, assumes a missing turn completed, or replays
   * a model. Enforces frozen session and actual original turn owner/project/session.
   */
  public Progress inspect(Access access) {
    SourceSemanticTaskState task = bound(access);
    if (task.activeTurnId() == null) {
      return new Progress(task, null, Outcome.NO_ACTIVE_TURN, task.nextChunkId());
    }
    Optional<AgentTurnRecord> original = turns.findByTurnId(task.activeTurnId());
    if (original.isEmpty()) {
      return new Progress(task, null, Outcome.ORIGINAL_TURN_MISSING, task.nextChunkId());
    }
    ensureOriginal(access, original.get(), task.activeTurnId());
    return new Progress(task, original.get().status(), statusOutcome(original.get().status()),
        task.nextChunkId());
  }

  /**
   * Called after a durable original turn has been submitted. On COMPLETED, advance only
   * after BOTH fresh source and approved on-disk plan are rechecked and an immutable
   * source-bound result receipt is read. WAITING_INPUT stays owned by the original turn.
   */
  public Progress reconcile(Access access) {
    Progress view = inspect(access);
    SourceSemanticTaskState task = view.task();
    if (task.activeTurnId() == null || view.outcome() == Outcome.ORIGINAL_TURN_MISSING) {
      // Missing row may be a reserve->enqueue crash or a racing enqueue; do not guess.
      return view;
    }
    if (view.originalStatus() == TurnStatus.QUEUED
        || view.originalStatus() == TurnStatus.RUNNING
        || view.originalStatus() == TurnStatus.WAITING_INPUT) {
      return view;
    }
    if (task.status() != SourceSemanticTaskState.Status.RUNNING
        && task.status() != SourceSemanticTaskState.Status.PAUSE_REQUESTED) {
      // Cancellation wins: an old COMPLETED callback cannot resurrect this task.
      return view;
    }
    if (view.originalStatus() == TurnStatus.FAILED
        || view.originalStatus() == TurnStatus.CANCELLED
        || view.originalStatus() == TurnStatus.INTERRUPTED) {
      var interrupted = ledger.interrupt(access.ownerId(), access.projectId(),
          access.taskId(), task.activeTurnId());
      return new Progress(interrupted, view.originalStatus(), Outcome.ORIGINAL_TURN_INTERRUPTED,
          interrupted.nextChunkId());
    }
    if (view.originalStatus() != TurnStatus.COMPLETED) {
      throw new IllegalStateException("[F039_UNKNOWN_ORIGINAL_TURN_STATE]");
    }

    String verifiedScope = freshSource.reauthorizeAndFingerprint(access, task);
    if (!task.scopeFingerprint().equals(verifiedScope)) {
      throw new IllegalStateException("[F039_SOURCE_OR_AUTHORIZATION_DRIFT]");
    }
    planFiles.verifyApproved(access.trustedWorkspace(), task.planSha256());
    AgentTurnRecord completed = turns.findByTurnId(task.activeTurnId())
        .orElseThrow(() -> new IllegalStateException("[F039_ORIGINAL_TURN_DISAPPEARED]"));
    ensureOriginal(access, completed, task.activeTurnId());
    if (completed.status() != TurnStatus.COMPLETED) {
      throw new IllegalStateException("[F039_ORIGINAL_TURN_CHANGED]");
    }
    Optional<Receipt> verified = receipts.read(completed, task.activeChunkId());
    if (verified.isEmpty()) {
      return new Progress(task, TurnStatus.COMPLETED, Outcome.RECEIPT_NOT_VERIFIED,
          task.nextChunkId());
    }
    ensureReceipt(access, task, verified.get());
    var advanced = ledger.completeTurn(access.ownerId(), access.projectId(), access.taskId(),
        verifiedScope, task.planSha256(), task.activeChunkId(),
        task.activeTurnId(), verified.get().resultSha256());
    return new Progress(advanced, TurnStatus.COMPLETED, Outcome.CHUNK_VERIFIED,
        advanced.nextChunkId());
  }

  /**
   * Cancel task first using durable CAS so all late receipts are rejected, then forward the
   * frozen turn ID to the original exact-turn stop path. Stop failures are propagated, but
   * cancellation of the auxiliary task remains durable and no new chunk is authorized.
   */
  public SourceSemanticTaskState cancelAndStop(Access access) {
    SourceSemanticTaskState task = bound(access);
    if (task.activeTurnId() != null) {
      turns.findByTurnId(task.activeTurnId())
          .ifPresent(original -> ensureOriginal(access, original, task.activeTurnId()));
    }
    var cancelled = ledger.cancel(access.ownerId(), access.projectId(), access.taskId());
    if (task.activeTurnId() != null) {
      stop.stop(task.activeTurnId());
    }
    return cancelled;
  }

  private SourceSemanticTaskState bound(Access access) {
    Objects.requireNonNull(access);
    var task = ledger.read(access.ownerId(), access.projectId(), access.taskId());
    // Old unbound #500 state remains readable by its SDK owner but cannot be executed here.
    if (task.sessionId() == null || !task.sessionId().equals(access.sessionId())) {
      throw new IllegalArgumentException("[F039_SESSION_BINDING_MISSING_OR_MISMATCH]");
    }
    return task;
  }

  private static void ensureOriginal(Access access, AgentTurnRecord original, String expectedTurn) {
    if (!Objects.equals(expectedTurn, original.turnId())
        || !Objects.equals(access.sessionId(), original.sessionId())
        || original.projectId() != access.projectId()
        || original.userId() != numericOwner(access.ownerId())) {
      throw new IllegalArgumentException("[F039_ORIGINAL_TURN_SCOPE_MISMATCH]");
    }
  }

  private static void ensureReceipt(Access access, SourceSemanticTaskState task, Receipt receipt) {
    if (!Objects.equals(receipt.turnId(), task.activeTurnId())
        || !Objects.equals(receipt.chunkId(), task.activeChunkId())
        || !Objects.equals(receipt.sessionId(), task.sessionId())
        || receipt.projectId() != access.projectId()
        || receipt.userId() != numericOwner(access.ownerId())
        || !Objects.equals(receipt.sourceFingerprint(), task.scopeFingerprint())
        || !Objects.equals(receipt.planSha256(), task.planSha256())) {
      throw new IllegalArgumentException("[F039_RESULT_RECEIPT_SCOPE_MISMATCH]");
    }
    if (receipt.resultSha256() == null || !receipt.resultSha256().matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("[F039_INVALID_RESULT_DIGEST]");
    }
  }

  private static long numericOwner(String ownerId) {
    try {
      long parsed = Long.parseLong(ownerId);
      if (parsed <= 0) throw new NumberFormatException("nonpositive");
      return parsed;
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("[F039_OWNER_ID_NOT_NUMERIC]", invalid);
    }
  }

  private static Outcome statusOutcome(TurnStatus status) {
    return switch (status) {
      case QUEUED -> Outcome.QUEUED;
      case RUNNING -> Outcome.RUNNING;
      case WAITING_INPUT -> Outcome.WAITING_INPUT;
      case COMPLETED -> Outcome.RECEIPT_NOT_VERIFIED;
      case FAILED, CANCELLED, INTERRUPTED -> Outcome.ORIGINAL_TURN_INTERRUPTED;
    };
  }
}
