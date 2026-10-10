package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.core.state.InMemoryAgentStateStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceSemanticPauseAfterTurnTest {
  private static final String PLAN = SourceSemanticScope.digest(List.of("approved-plan"));

  @Test void pauseRunningWaitsForVerifiedOriginalAndNeverStartsNextChunk() {
    var scope=SourceSemanticScopeChunkContractTest.scope();
    var ledger=new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
    var planned=ledger.create("pause-task","42","session-1",scope,1,2,PLAN,10,20);
    assertTrue(planned.chunkIds().size() > 1);
    ledger.confirmPlan("42",31,"pause-task",scope.fingerprint(),PLAN);
    var first=planned.chunkIds().get(0);
    ledger.reserveTurn("42",31,"pause-task",scope.fingerprint(),PLAN,first,"turn-a",2);
    var pauseRequested=ledger.pause("42",31,"pause-task");
    assertEquals(SourceSemanticTaskState.Status.PAUSE_REQUESTED,pauseRequested.status());
    assertEquals("turn-a",pauseRequested.activeTurnId());
    assertThrows(IllegalStateException.class,()->ledger.reserveTurn(
        "42",31,"pause-task",scope.fingerprint(),PLAN,first,"turn-b",2));
    var completed=ledger.completeTurn("42",31,"pause-task",scope.fingerprint(),PLAN,
        first,"turn-a",SourceSemanticScope.digest(List.of("verified-artifact")));
    assertEquals(SourceSemanticTaskState.Status.PAUSED,completed.status());
    assertEquals(1,completed.completedChunkIds().size());
    assertEquals(1,completed.usedTurns());
    assertEquals(2,completed.reservedToolCalls());
    assertThrows(IllegalStateException.class,()->ledger.reserveTurn(
        "42",31,"pause-task",scope.fingerprint(),PLAN,completed.nextChunkId(),"turn-c",1));
    ledger.resume("42",31,"pause-task",scope.fingerprint(),PLAN);
    var next=ledger.reserveTurn("42",31,"pause-task",scope.fingerprint(),PLAN,
        completed.nextChunkId(),"turn-new",2);
    assertEquals(2,next.usedTurns());
  }

  @Test void cancellationDuringPauseRequestRejectsAllLateCompletions() {
    var scope=SourceSemanticScopeChunkContractTest.scope();
    var ledger=new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
    var original=ledger.create("pause-cancel","42","session-1",scope,1,2,PLAN,10,20);
    ledger.confirmPlan("42",31,"pause-cancel",scope.fingerprint(),PLAN);
    ledger.reserveTurn("42",31,"pause-cancel",scope.fingerprint(),PLAN,
        original.chunkIds().get(0),"turn-one",1);
    ledger.pause("42",31,"pause-cancel");
    ledger.cancel("42",31,"pause-cancel");
    assertThrows(IllegalStateException.class,()->ledger.completeTurn("42",31,
        "pause-cancel",scope.fingerprint(),PLAN,original.chunkIds().get(0),
        "turn-one",SourceSemanticScope.digest(List.of("too-late"))));
    assertEquals(SourceSemanticTaskState.Status.CANCELLED,
        ledger.read("42",31,"pause-cancel").status());
  }
}
