package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.runtime.SourceSemanticPlanDocumentGuard;
import io.yak.ops.business.agent.runtime.SourceSemanticScope;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskLedger;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSemanticOriginalTurnReconcilerTest {
  @TempDir Path workspace;

  private final AgentTurnRepository originals = mock(AgentTurnRepository.class);
  private final SourceSemanticTaskLedger ledger =
      new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
  private final SourceSemanticPlanDocumentGuard files = new SourceSemanticPlanDocumentGuard();
  private final AtomicReference<String> currentSource = new AtomicReference<>();
  private final AtomicReference<Optional<SourceSemanticOriginalTurnReconciler.Receipt>> result =
      new AtomicReference<>(Optional.empty());
  private final List<String> stopped = new ArrayList<>();

  private SourceSemanticOriginalTurnReconciler bridge;
  private SourceSemanticOriginalTurnReconciler.Access access;
  private SourceSemanticScope scope;
  private String plan;
  private String chunk;

  @BeforeEach void setUp() throws Exception {
    Files.createDirectory(workspace.resolve("plans"));
    Files.writeString(workspace.resolve("plans/PLAN.md"), "# Human-reviewed no-write plan\n");
    plan = files.readReview(workspace).sha256();
    scope = new SourceSemanticScope(31, "source-1", "warehouse", "public", "capture-v1",
        List.of(new SourceSemanticScope.Table("orders", "table-hash",
            List.of("id", "amount"))));
    currentSource.set(scope.fingerprint());
    access = new SourceSemanticOriginalTurnReconciler.Access(
        "42", 31, "task-1", "session-1", workspace);
    bridge = new SourceSemanticOriginalTurnReconciler(ledger, originals, files,
        (identity, frozen) -> currentSource.get(), (record, id) -> result.get(),
        stopped::add);
    var state = ledger.create("task-1", "42", "session-1", scope, 1, 1, plan, 5, 10);
    ledger.confirmPlan("42", 31, "task-1", scope.fingerprint(), plan);
    chunk = state.chunkIds().get(0);
  }

  private AgentTurnRecord original(TurnStatus status) {
    return new AgentTurnRecord("turn-1", "session-1", 42L, 31L, TurnKind.START,
        "trusted-turn-input", status, null, null, null, null, null);
  }

  private SourceSemanticTaskState active() {
    return ledger.reserveTurn("42",31,"task-1",scope.fingerprint(),plan,chunk,"turn-1",2);
  }

  private SourceSemanticOriginalTurnReconciler.Receipt validReceipt() {
    return new SourceSemanticOriginalTurnReconciler.Receipt("turn-1",chunk,
        "session-1",31,42,scope.fingerprint(),plan,
        SourceSemanticScopeDigest.sha("trusted-result"));
  }

  @Test void originalQueuedRunningAndWaitingNeverBecomeFakeCompletion() {
    var task = active();
    for (var status : List.of(TurnStatus.QUEUED,TurnStatus.RUNNING,TurnStatus.WAITING_INPUT)) {
      when(originals.findByTurnId("turn-1")).thenReturn(Optional.of(original(status)));
      var observed = bridge.inspect(access);
      assertEquals(status,observed.originalStatus());
      var view = bridge.reconcile(access);
      assertEquals(status,view.originalStatus());
      assertEquals(task,ledger.read("42",31,"task-1"));
      assertTrue(ledger.read("42",31,"task-1").completedChunkIds().isEmpty());
    }
    verifyNoMoreInteractions(originals); // Reads only: no direct repository transitions.
  }

  @Test void completedTurnCannotAdvanceWithoutVerifiedImmutableReceipt() {
    active();
    when(originals.findByTurnId("turn-1"))
        .thenReturn(Optional.of(original(TurnStatus.COMPLETED)));
    assertEquals(SourceSemanticOriginalTurnReconciler.Outcome.RECEIPT_NOT_VERIFIED,
        bridge.reconcile(access).outcome());
    assertTrue(ledger.read("42",31,"task-1").completedChunkIds().isEmpty());
    result.set(Optional.of(validReceipt()));
    var completed = bridge.reconcile(access);
    assertEquals(SourceSemanticOriginalTurnReconciler.Outcome.CHUNK_VERIFIED,completed.outcome());
    var task = ledger.read("42",31,"task-1");
    assertEquals(List.of(chunk),task.completedChunkIds());
    assertEquals("turn-1",task.completedTurnIds().get(chunk));
    assertEquals(validReceipt().resultSha256(),task.resultDigests().get(chunk));
    assertEquals(1,task.usedTurns());
    assertEquals(2,task.reservedToolCalls());
    // No duplicate completion: task already moved away from RUNNING.
    assertEquals(SourceSemanticOriginalTurnReconciler.Outcome.NO_ACTIVE_TURN,
        bridge.reconcile(access).outcome());
  }

  @Test void sourcePlanAndReceiptMismatchNeverAdvance() throws Exception {
    active();
    when(originals.findByTurnId("turn-1"))
        .thenReturn(Optional.of(original(TurnStatus.COMPLETED)));
    result.set(Optional.of(validReceipt()));
    currentSource.set(SourceSemanticScopeDigest.sha("changed-capture"));
    assertThrows(IllegalStateException.class,() -> bridge.reconcile(access));
    currentSource.set(scope.fingerprint());
    Files.writeString(workspace.resolve("plans/PLAN.md"), "# Changed by another actor\n");
    assertThrows(IllegalStateException.class,() -> bridge.reconcile(access));
    Files.writeString(workspace.resolve("plans/PLAN.md"), "# Human-reviewed no-write plan\n");
    var valid = validReceipt();
    result.set(Optional.of(new SourceSemanticOriginalTurnReconciler.Receipt(
        valid.turnId(),valid.chunkId(),"wrong-session",
        valid.projectId(),valid.userId(),valid.sourceFingerprint(),valid.planSha256(),
        valid.resultSha256())));
    assertThrows(IllegalArgumentException.class,() -> bridge.reconcile(access));
    assertTrue(ledger.read("42",31,"task-1").completedChunkIds().isEmpty());
  }

  @Test void originalTurnCrossSessionOrProjectIsRefused() {
    active();
    var fake = new AgentTurnRecord("turn-1","session-other",42L,31L,TurnKind.START,
        "input",TurnStatus.COMPLETED,null,null,null,null,null);
    when(originals.findByTurnId("turn-1")).thenReturn(Optional.of(fake));
    assertThrows(IllegalArgumentException.class,()->bridge.inspect(access));
    assertThrows(IllegalArgumentException.class,()->bridge.cancelAndStop(access));
    assertTrue(stopped.isEmpty());
    assertEquals(SourceSemanticTaskState.Status.RUNNING,
        ledger.read("42",31,"task-1").status());
    assertThrows(IllegalArgumentException.class,()->bridge.inspect(
        new SourceSemanticOriginalTurnReconciler.Access("42",31,"task-1","other",workspace)));
    assertThrows(IllegalArgumentException.class,()->bridge.inspect(
        new SourceSemanticOriginalTurnReconciler.Access("42",32,"task-1","session-1",workspace)));
  }

  @Test void cancellationWinsOverLateResultAndStopsExactTurnOnly() {
    active();
    when(originals.findByTurnId("turn-1"))
        .thenReturn(Optional.of(original(TurnStatus.RUNNING)));
    bridge.cancelAndStop(access);
    assertEquals(List.of("turn-1"),stopped);
    assertEquals(SourceSemanticTaskState.Status.CANCELLED,
        ledger.read("42",31,"task-1").status());
    when(originals.findByTurnId("turn-1"))
        .thenReturn(Optional.of(original(TurnStatus.COMPLETED)));
    result.set(Optional.of(validReceipt()));
    bridge.reconcile(access);
    assertEquals(SourceSemanticTaskState.Status.CANCELLED,
        ledger.read("42",31,"task-1").status());
    assertTrue(ledger.read("42",31,"task-1").completedChunkIds().isEmpty());
  }

  @Test void failedOrInterruptedOriginalRequiresExplicitNewTurn() {
    active();
    when(originals.findByTurnId("turn-1"))
        .thenReturn(Optional.of(original(TurnStatus.INTERRUPTED)));
    var res = bridge.reconcile(access);
    assertEquals(SourceSemanticOriginalTurnReconciler.Outcome.ORIGINAL_TURN_INTERRUPTED,
        res.outcome());
    assertEquals(SourceSemanticTaskState.Status.INTERRUPTED,
        ledger.read("42",31,"task-1").status());
    assertEquals(1,ledger.read("42",31,"task-1").usedTurns());
    assertThrows(IllegalStateException.class,() -> ledger.reserveTurn("42",31,"task-1",
        scope.fingerprint(),plan,chunk,"turn-2",2));
    ledger.resume("42",31,"task-1",scope.fingerprint(),plan);
    var replacement = ledger.reserveTurn("42",31,"task-1",
        scope.fingerprint(),plan,chunk,"turn-2",2);
    assertEquals("turn-2",replacement.activeTurnId());
    assertEquals(2,replacement.usedTurns()); // No refund of failed first turn.
  }

  @Test void missingOriginalDoesNotClaimSuccessOrGuessInterruption() {
    var old = active();
    when(originals.findByTurnId("turn-1")).thenReturn(Optional.empty());
    var viewed = bridge.reconcile(access);
    assertEquals(SourceSemanticOriginalTurnReconciler.Outcome.ORIGINAL_TURN_MISSING,
        viewed.outcome());
    assertEquals(old,ledger.read("42",31,"task-1"));
  }

  @Test void legacyUnboundTaskCannotBeReconciled() {
    var oldScope = scope;
    var old = ledger.create("legacy", "42", oldScope, 1,1,plan,2,3);
    assertNull(old.sessionId());
    assertThrows(IllegalArgumentException.class,() -> bridge.inspect(
        new SourceSemanticOriginalTurnReconciler.Access("42",31,"legacy","session-1",workspace)));
  }

  private static final class SourceSemanticScopeDigest {
    private static String sha(String text) {
      try {
        return java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      } catch (java.security.NoSuchAlgorithmException impossible) {
        throw new AssertionError(impossible);
      }
    }
  }
}
