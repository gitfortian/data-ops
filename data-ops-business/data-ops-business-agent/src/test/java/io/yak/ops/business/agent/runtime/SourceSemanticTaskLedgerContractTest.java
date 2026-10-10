package io.yak.ops.business.agent.runtime;
import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.core.state.InMemoryAgentStateStore;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SourceSemanticTaskLedgerContractTest {
  private static final String PLAN=SourceSemanticScope.digest(List.of("plan-revision-1"));

  @Test void reserveBeforeTurnAndNeverRefundOnInterruption() {
    var ledger=new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
    var scope=SourceSemanticScopeChunkContractTest.scope();
    var created=ledger.create("task-a","alice",scope,1,2,PLAN,2,3);
    String chunk=created.chunkIds().get(0);
    assertEquals(SourceSemanticTaskState.Status.PLANNED,created.status());
    assertThrows(IllegalStateException.class,()->ledger.reserveTurn(
        "alice",31,"task-a",scope.fingerprint(),PLAN,chunk,"turn-1",1));
    ledger.confirmPlan("alice",31,"task-a",scope.fingerprint(),PLAN);
    ledger.reserveTurn("alice",31,"task-a",scope.fingerprint(),PLAN,chunk,"turn-1",2);
    assertThrows(IllegalStateException.class,()->ledger.reserveTurn(
        "alice",31,"task-a",scope.fingerprint(),PLAN,chunk,"duplicate",1));
    ledger.interrupt("alice",31,"task-a","turn-1");
    assertThrows(IllegalStateException.class,()->ledger.reserveTurn(
        "alice",31,"task-a",scope.fingerprint(),PLAN,chunk,"unapproved",1));
    ledger.resume("alice",31,"task-a",scope.fingerprint(),PLAN);
    ledger.reserveTurn("alice",31,"task-a",scope.fingerprint(),PLAN,chunk,"turn-2",1);
    var s=ledger.read("alice",31,"task-a");
    assertEquals(2,s.usedTurns());
    assertEquals(3,s.reservedToolCalls());
    var result=SourceSemanticScope.digest(List.of("result"));
    ledger.completeTurn("alice",31,"task-a",scope.fingerprint(),PLAN,chunk,"turn-2",result);
    assertEquals(result,ledger.read("alice",31,"task-a").resultDigests().get(chunk));
    assertThrows(IllegalStateException.class,()->ledger.reserveTurn(
        "alice",31,"task-a",scope.fingerprint(),PLAN,
        ledger.read("alice",31,"task-a").nextChunkId(),"turn-3",1));
  }
  @Test void crossProjectAndLateCompletionFailClosed() {
    var ledger=new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
    var scope=SourceSemanticScopeChunkContractTest.scope();
    var created=ledger.create("task-b","alice",scope,1,2,PLAN,6,10);
    assertThrows(IllegalArgumentException.class,()->ledger.read("bob",31,"task-b"));
    assertThrows(IllegalArgumentException.class,()->ledger.read("alice",32,"task-b"));
    assertThrows(IllegalStateException.class,()->ledger.confirmPlan("alice",31,
        "task-b","source-drift",PLAN));
    ledger.confirmPlan("alice",31,"task-b",scope.fingerprint(),PLAN);
    assertThrows(IllegalStateException.class,()->ledger.reserveTurn("alice",31,
        "task-b",scope.fingerprint(),PLAN,created.chunkIds().get(1),"skipped-chunk",1));
    ledger.reserveTurn("alice",31,"task-b",scope.fingerprint(),PLAN,
        created.chunkIds().get(0),"turn-a",1);
    ledger.cancel("alice",31,"task-b");
    assertThrows(IllegalStateException.class,()->ledger.completeTurn("alice",31,
        "task-b",scope.fingerprint(),PLAN,created.chunkIds().get(0),"turn-a",
        SourceSemanticScope.digest(List.of("too-late"))));
    assertEquals(SourceSemanticTaskState.Status.CANCELLED,
        ledger.read("alice",31,"task-b").status());
  }
  @Test void completedTaskIsTerminalAndReferencesTurnOnly() {
    var ledger=new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
    var scope=SourceSemanticScopeChunkContractTest.scope();
    var state=ledger.create("task-c","alice",scope,20,500,PLAN,2,4);
    assertEquals(1,state.chunkIds().size());
    ledger.confirmPlan("alice",31,"task-c",scope.fingerprint(),PLAN);
    ledger.reserveTurn("alice",31,"task-c",scope.fingerprint(),PLAN,
        state.chunkIds().get(0),"turn-c",1);
    var digest=SourceSemanticScope.digest(List.of("evidence"));
    ledger.completeTurn("alice",31,"task-c",scope.fingerprint(),PLAN,
        state.chunkIds().get(0),"turn-c",digest);
    var done=ledger.read("alice",31,"task-c");
    assertEquals(SourceSemanticTaskState.Status.COMPLETED,done.status());
    assertEquals("turn-c",done.completedTurnIds().get(state.chunkIds().get(0)));
    assertEquals(digest,done.resultDigests().get(state.chunkIds().get(0)));
    assertThrows(IllegalStateException.class,()->ledger.cancel("alice",31,"task-c"));
  }
  @Test void concurrentReservationsHaveOneWinner() throws Exception {
    var ledger=new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
    var scope=SourceSemanticScopeChunkContractTest.scope();
    var state=ledger.create("task-race","alice",scope,1,2,PLAN,4,4);
    ledger.confirmPlan("alice",31,"task-race",scope.fingerprint(),PLAN);
    var go=new CountDownLatch(1);
    try(var workers=Executors.newFixedThreadPool(2)) {
      var tasks=List.of("turn-a","turn-b").stream().map(id->workers.submit(()->{
        go.await();
        try { ledger.reserveTurn("alice",31,"task-race",scope.fingerprint(),PLAN,
            state.chunkIds().get(0),id,1);return true; }
        catch(IllegalStateException expected){return false;}
      })).toList();
      go.countDown();
      int successes=0;
      for(var task:tasks) if(task.get(5,TimeUnit.SECONDS)) successes++;
      assertEquals(1,successes);
      assertEquals(1,ledger.read("alice",31,"task-race").usedTurns());
      assertEquals(1,ledger.read("alice",31,"task-race").reservedToolCalls());
    }
  }
  @Test void rejectNonVersionedStore() {
    var store=org.mockito.Mockito.mock(io.agentscope.core.state.AgentStateStore.class);
    assertThrows(IllegalStateException.class,()->new SourceSemanticTaskLedger(store));
  }
}
