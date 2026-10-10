package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.runtime.SourceSemanticPlanDocumentGuard;
import io.yak.ops.business.agent.runtime.SourceSemanticScope;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskLedger;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SourceSemanticVerifiedTurnAdmissionTest {
  @TempDir Path workspace;
  private final SourceSemanticPlanDocumentGuard planFiles = new SourceSemanticPlanDocumentGuard();
  private final SourceSemanticTaskLedger ledger =
      new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
  private final AgentChatService chat = mock(AgentChatService.class);
  private final AtomicReference<SourceSemanticScope> current = new AtomicReference<>();
  private SourceSemanticVerifiedTurnAdmission adapter;
  private SourceSemanticOriginalTurnReconciler.Access access;
  private SourceSemanticScope frozen;
  private String approvedPlan;

  @BeforeEach void setup() throws Exception {
    Files.createDirectory(workspace.resolve("plans"));
    Files.writeString(workspace.resolve("plans/PLAN.md"), "# Scope review\nOnly approved schema\n");
    approvedPlan = planFiles.readReview(workspace).sha256();
    frozen = new SourceSemanticScope(31, "source-1", "warehouse", "public", "capture-4",
        List.of(new SourceSemanticScope.Table("orders", "table-hash",
            List.of("id", "amount", "buyer"))));
    current.set(frozen);
    access = new SourceSemanticOriginalTurnReconciler.Access(
        "42",31,"source-task-1","original-session",workspace);
    adapter = new SourceSemanticVerifiedTurnAdmission(ledger, planFiles, chat,
        (identity, snapshot) -> current.get());
    ledger.create(access.taskId(),access.ownerId(),access.sessionId(),frozen,1,2,
        approvedPlan,4,6);
    ledger.confirmPlan(access.ownerId(),access.projectId(),access.taskId(),
        frozen.fingerprint(),approvedPlan);
  }

  @Test void oneVerifiedChunkReservesBeforeOriginalEnqueueWithoutModelCall() {
    doAnswer(call -> {
      String turnId=call.getArgument(1);
      assertEquals(SourceSemanticTaskState.Status.RUNNING,
          ledger.read("42",31,access.taskId()).status());
      assertEquals(turnId,ledger.read("42",31,access.taskId()).activeTurnId());
      return null;
    }).when(chat).enqueueReservedSourceSemanticTurn(anyString(), anyString(),
        anyString(), eq(42L),eq(31L),eq("source-task-1"));
    var submitted=adapter.admitNext(access,2);
    var state=ledger.read("42",31,access.taskId());
    assertEquals(1,state.usedTurns());
    assertEquals(2,state.reservedToolCalls());
    assertEquals("original-session",state.sessionId());
    assertEquals(frozen,state.sourceManifest());
    assertEquals(submitted.turnId(),state.activeTurnId());
    assertEquals(submitted.chunkId(),state.activeChunkId());
    assertEquals(state.chunkIds(),state.frozenChunks().stream().map(c->c.id()).toList());
    assertEquals(2,state.frozenChunks().size());
    var prompt=ArgumentCaptor.forClass(String.class);
    verify(chat).assertSourceSemanticOwner("original-session",42L,31L);
    verify(chat).enqueueReservedSourceSemanticTurn(eq("original-session"),
        eq(submitted.turnId()),prompt.capture(),eq(42L),eq(31L),eq("source-task-1"));
    assertTrue(prompt.getValue().contains(frozen.fingerprint()));
    assertTrue(prompt.getValue().contains(approvedPlan));
    assertTrue(prompt.getValue().contains(submitted.chunkId()));
    assertTrue(prompt.getValue().contains("orders"));
    assertFalse(prompt.getValue().contains("secret-token"));
    // There is exactly one durable ledger reservation; never another turn while RUNNING.
    assertThrows(IllegalStateException.class,()->adapter.admitNext(access,1));
    verify(chat,times(1)).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
        anyString(),anyLong(),anyLong(),eq("source-task-1"));
  }

  @Test void driftOrWorkspaceEditFailBeforeReservation() throws Exception {
    current.set(new SourceSemanticScope(31,"source-1","warehouse","public","capture-5",
        frozen.tables()));
    assertThrows(IllegalStateException.class,()->adapter.admitNext(access,1));
    current.set(frozen);
    Files.writeString(workspace.resolve("plans/PLAN.md"), "# Other plan\n");
    assertThrows(IllegalStateException.class,()->adapter.admitNext(access,1));
    var state=ledger.read("42",31,access.taskId());
    assertEquals(0,state.usedTurns());
    verify(chat,never()).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
        anyString(),anyLong(),anyLong(),eq("source-task-1"));
  }

  @Test void wrongSessionAndProjectCannotReserve() {
    assertThrows(IllegalStateException.class,()->adapter.admitNext(
        new SourceSemanticOriginalTurnReconciler.Access("42",31,access.taskId(),
            "another-session",workspace),1));
    assertThrows(IllegalArgumentException.class,()->adapter.admitNext(
        new SourceSemanticOriginalTurnReconciler.Access("42",32,access.taskId(),
            "original-session",workspace),1));
    assertEquals(0,ledger.read("42",31,access.taskId()).usedTurns());
    verify(chat,never()).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
        anyString(),anyLong(),anyLong(),eq("source-task-1"));
  }

  @Test void definitiveOrAmbiguousQueueFailureNeverRefundsOrReplaysTurn() {
    doThrow(new IllegalStateException("DB connection lost after insert"))
        .when(chat).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
            anyString(),eq(42L),eq(31L),eq("source-task-1"));
    var failure=assertThrows(IllegalStateException.class,()->adapter.admitNext(access,2));
    assertTrue(failure.getMessage().contains("F039_RESERVED_ADMISSION_OUTCOME_UNCERTAIN"));
    var state=ledger.read("42",31,access.taskId());
    assertEquals(SourceSemanticTaskState.Status.RUNNING,state.status());
    assertEquals(1,state.usedTurns());
    assertEquals(2,state.reservedToolCalls());
    assertNotNull(state.activeTurnId());
    assertThrows(IllegalStateException.class,()->adapter.admitNext(access,2));
    verify(chat,times(1)).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
        anyString(),anyLong(),anyLong(),eq("source-task-1"));
  }

  @Test void initialActorValidationFailureLeavesBudgetUntouched() {
    doThrow(new IllegalArgumentException("no current principal"))
        .when(chat).assertSourceSemanticOwner(anyString(),anyLong(),anyLong(),eq("source-task-1"));
    assertThrows(IllegalArgumentException.class,()->adapter.admitNext(access,2));
    assertEquals(0,ledger.read("42",31,access.taskId()).reservedToolCalls());
    verify(chat,never()).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
        anyString(),anyLong(),anyLong(),eq("source-task-1"));
  }

  @Test void legacyTaskMustNotEnterAdmission() {
    ledger.create("legacy-task","42",frozen,1,2,approvedPlan,3,5);
    var legacyAccess=new SourceSemanticOriginalTurnReconciler.Access(
        "42",31,"legacy-task","original-session",workspace);
    assertThrows(IllegalStateException.class,()->adapter.admitNext(legacyAccess,2));
    assertEquals(0,ledger.read("42",31,"legacy-task").usedTurns());
  }
}
