package io.yak.ops.business.agent.conversation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.core.security.UserExecutionScope;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Live identity recovery and original-turn correlation govern server follow-up. */
class SourceSemanticAutoContinuationTest {
  @Test void completedOriginalSchedulesExactlyNextBoundChunkUnderLiveIdentity() {
    UserExecutionScope scope=mock(UserExecutionScope.class);
    SourceSemanticTaskFacade tasks=mock(SourceSemanticTaskFacade.class);
    when(scope.call(eq(42L),eq(31L),any())).thenAnswer(inv ->
        ((Supplier<?>)inv.getArgument(2)).get());
    var view=mock(SourceSemanticTaskFacade.TaskView.class);
    when(view.status()).thenReturn("READY");
    when(view.nextChunkId()).thenReturn("next-chunk");
    when(view.sessionId()).thenReturn("source-session");
    when(view.completedTurnIds()).thenReturn(Map.of("first","original-turn"));
    when(tasks.read("be1f16a4-4a71-4f15-b90c-210130735170")).thenReturn(view);
    var original=new AgentTurnRecord("original-turn","source-session",42L,31L,
        TurnKind.START,"{}",TurnStatus.COMPLETED,null,null,null,null,null);
    var coordinator=new SourceSemanticAutoContinuation(scope,tasks);
    try {
      coordinator.completed(original,"be1f16a4-4a71-4f15-b90c-210130735170");
      // The production taskId, not test alias, is the durable call key.
      verify(tasks,timeout(3000)).next("be1f16a4-4a71-4f15-b90c-210130735170");
    } finally { coordinator.shutdown(); }
  }

  @Test void deniedLiveSecurityStopsFollowUpBeforeReadingAnySource() {
    UserExecutionScope scope=mock(UserExecutionScope.class);
    SourceSemanticTaskFacade tasks=mock(SourceSemanticTaskFacade.class);
    when(scope.call(eq(42L),eq(31L),any())).thenThrow(new IllegalStateException("revoked"));
    var original=new AgentTurnRecord("original-turn","source-session",42L,31L,
        TurnKind.START,"{}",TurnStatus.COMPLETED,null,null,null,null,null);
    var coordinator=new SourceSemanticAutoContinuation(scope,tasks);
    try {
      coordinator.completed(original,"be1f16a4-4a71-4f15-b90c-210130735170");
      verify(scope,timeout(3000)).call(eq(42L),eq(31L),any());
      verifyNoInteractions(tasks);
    } finally { coordinator.shutdown(); }
  }
}
