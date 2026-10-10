package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.runtime.SourceSemanticPlanDocumentGuard;
import io.yak.ops.business.agent.runtime.SourceSemanticScope;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSemanticTurnFenceTest {
  @TempDir Path workspace;

  @Test void rejectsStaleCanceledOrCrossSessionOriginalBeforeInference() throws Exception {
    var store=new InMemoryAgentStateStore();
    var ledger=new SourceSemanticTaskLedger(store);
    var scope=new SourceSemanticScope(31,"7","warehouse","public","capture-1",
        List.of(new SourceSemanticScope.Table("orders","table-hash",List.of("id"))));
    String taskId="be1f16a4-4a71-4f15-b90c-210130735170";
    String turnId="fbea0c9c-7168-4f1c-82b7-d553da7d9a7d";
    Path dir=workspace.resolve("31/42/"+taskId+"/plans");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("PLAN.md"),"# Approved task plan\n");
    String plan=new SourceSemanticPlanDocumentGuard()
        .readReview(workspace.resolve("31/42/"+taskId)).sha256();
    var state=ledger.create(taskId,"42","session-1",scope,1,1,plan,2,4);
    var fence=new SourceSemanticTurnFence(store,workspace.toString());
    var input=TurnInput.ofStart("u","a","bounded metadata").withSourceTask(taskId);
    var record=new AgentTurnRecord(turnId,"session-1",42,31,TurnKind.START,"{}",
        TurnStatus.QUEUED,null,null,null,null,null);
    assertThrows(IllegalStateException.class,()->fence.requireActive(record,input));
    ledger.confirmPlan("42",31,taskId,scope.fingerprint(),plan);
    ledger.reserveTurn("42",31,taskId,scope.fingerprint(),plan,
        state.chunkIds().get(0),turnId,1);
    assertDoesNotThrow(()->fence.requireActive(record,input));
    var foreign=new AgentTurnRecord(turnId,"other-session",42,31,TurnKind.START,"{}",
        TurnStatus.QUEUED,null,null,null,null,null);
    assertThrows(IllegalStateException.class,()->fence.requireActive(foreign,input));
    ledger.cancel("42",31,taskId);
    assertThrows(IllegalStateException.class,()->fence.requireActive(record,input));
  }
}
