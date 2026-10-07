package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.business.agent.runtime.TurnSubscription;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

class AgentTurnStopTest {
  private final AgentTurnRepository turns = mock(AgentTurnRepository.class);
  private final SessionRepository sessions = mock(SessionRepository.class);
  private final AgentTurnRegistry registry = new AgentTurnRegistry();
  private final AgentRuntime runtime = mock(AgentRuntime.class);
  private final AgentChatService service = new AgentChatService(
      new AgentSessionOwnerValidator(sessions), mock(AgentStreamCoordinator.class),
      mock(AgentEventStreamTailer.class), mock(AgentTurnDispatcher.class), registry, turns,
      mock(AgentTurnEventRepository.class), sessions, runtime,
      () -> Optional.of(new ProjectContext(1L, "test-project")));
  private MockedStatic<YakSecurityContext> security;

  @BeforeEach void before() {
    security = mockStatic(YakSecurityContext.class);
    security.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
    when(sessions.findBySessionId("s1")).thenReturn(Optional.of(new SessionMeta("s1", 42L, 1L, null, null, null)));
  }
  @AfterEach void after() { security.close(); }

  private void turn(TurnStatus status, long user, long project) {
    when(turns.findByTurnId("old")).thenReturn(Optional.of(new AgentTurnRecord(
        "old", "s1", user, project, TurnKind.START, "{}", status, null, null, null, null, null)));
  }

  @Test void queuedStopUsesOnlyFrozenTurnId() {
    turn(TurnStatus.QUEUED, 42L, 1L);
    service.cancelTurn("old");
    verify(turns).cancelQueued("old");
    verify(turns, never()).cancelQueuedBySession("s1");
    verifyNoInteractions(runtime);
  }

  @Test void runningStopFinalizesBeforeDisposeAndKeepsLaterSessionHandle() {
    turn(TurnStatus.RUNNING, 42L, 1L);
    var old = mock(TurnSubscription.class);
    var next = mock(TurnSubscription.class);
    var finalizeOld = mock(Runnable.class);
    registry.register("s1", "old", old, finalizeOld);
    // A late old request cannot remove a new session index or touch its subscription.
    registry.register("s1", "next", next, () -> {});
    service.cancelTurn("old");
    var order = inOrder(finalizeOld, old);
    order.verify(finalizeOld).run(); order.verify(old).dispose();
    assertEquals(Optional.of("next"), registry.runningTurnId("s1"));
    verifyNoInteractions(next);
    verify(turns, never()).cancelQueuedBySession("s1");
  }

  @ParameterizedTest
  @EnumSource(value = TurnStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED"})
  void terminalStopIsIdempotentAndCannotStopNewTurn(TurnStatus status) {
    turn(status, 42L, 1L);
    var next = mock(TurnSubscription.class);
    registry.register("s1", "next", next, () -> {});
    service.cancelTurn("old"); service.cancelTurn("old");
    verifyNoInteractions(next, runtime);
    verify(turns, never()).cancelQueued("old");
    assertEquals(Optional.of("next"), registry.runningTurnId("s1"));
  }

  @Test void waitingStopCannotAlterPending() {
    turn(TurnStatus.WAITING_INPUT, 42L, 1L);
    assertThrows(TurnConflictException.class, () -> service.cancelTurn("old"));
    verify(turns, never()).cancelQueued("old"); verifyNoInteractions(runtime);
  }

  @ParameterizedTest
  @CsvSource({"7,1,42,1", "42,2,42,1", "42,1,7,1", "42,1,42,2"})
  void turnAndSessionMustBelongToUserAndProject(long user, long project, long sessionUser, long sessionProject) {
    turn(TurnStatus.RUNNING, user, project);
    when(sessions.findBySessionId("s1")).thenReturn(Optional.of(new SessionMeta("s1", sessionUser, sessionProject, null, null, null)));
    var subscription = mock(TurnSubscription.class);
    registry.register("s1", "old", subscription, () -> {});
    assertThrows(IllegalArgumentException.class, () -> service.cancelTurn("old"));
    verifyNoInteractions(subscription, runtime);
    verify(turns, never()).cancelQueued("old");
  }

  @Test void missingLoginOrSessionIsRejected() {
    security.when(YakSecurityContext::getCurrentUserId).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> service.cancelTurn("old"));
    verifyNoInteractions(turns);
    security.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
    assertThrows(IllegalArgumentException.class, () -> service.cancelTurn("old"));
    turn(TurnStatus.QUEUED, 42L, 1L);
    when(sessions.findBySessionId("s1")).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.cancelTurn("old"));
    verify(turns, never()).cancelQueued("old");
  }

  @Test void unavailableLocalHandleDoesNotGuessStoppedOrSelectAnotherTurn() {
    turn(TurnStatus.RUNNING, 42L, 1L);
    var next = mock(TurnSubscription.class);
    registry.register("s1", "next", next, () -> {});
    when(turns.cancelQueued("old")).thenReturn(false);
    service.cancelTurn("old");
    verify(turns).cancelQueued("old"); verifyNoInteractions(next);
    verify(turns, never()).cancelRunning("old");
  }

  @Test void concurrentQueuedStopAndClaimAlwaysConvergeWithoutOrphanHandle() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 20; i++) {
        var race = new AgentTurnRegistry();
        var state = new AtomicReference<>(TurnStatus.QUEUED);
        var start = new CountDownLatch(1);
        var subscription = mock(TurnSubscription.class);
        var claim = pool.submit(() -> {
          start.await();
          return race.claimAndRegister("s1", "t1", subscription,
              () -> state.compareAndSet(TurnStatus.RUNNING, TurnStatus.CANCELLED),
              () -> state.compareAndSet(TurnStatus.QUEUED, TurnStatus.RUNNING));
        });
        var stop = pool.submit(() -> {
          start.await();
          race.cancelTurn("t1", () -> state.compareAndSet(TurnStatus.QUEUED, TurnStatus.CANCELLED));
          return true;
        });
        start.countDown();
        boolean claimed = claim.get(5, TimeUnit.SECONDS);
        assertTrue(stop.get(5, TimeUnit.SECONDS));
        assertEquals(TurnStatus.CANCELLED, state.get());
        assertTrue(race.runningTurnId("s1").isEmpty());
        if (claimed) verify(subscription).dispose(); else verifyNoInteractions(subscription);
      }
    }
  }
}
