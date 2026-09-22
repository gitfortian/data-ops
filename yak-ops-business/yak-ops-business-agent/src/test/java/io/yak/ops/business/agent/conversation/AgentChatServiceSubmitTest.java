package io.yak.ops.business.agent.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.security.context.YakSecurityContext;

import java.util.List;
import java.util.Optional;
import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * 提交侧回归：新会话首访必须自动绑定归属（互斥重构曾误用 assertOwner 导致 404/500）；
 * 提交即入队返回，不做任何推理；同会话存在活跃轮次时拒绝重复提交。
 */
class AgentChatServiceSubmitTest {

  private static io.yak.ops.business.agent.domain.AgentTurnRecord waitingTurn(
      String turnId, String sessionId, long userId) {
    String payload =
        io.yak.ops.business.agent.repository.support.TurnInputCodec.encode(
            io.yak.ops.business.agent.domain.TurnInput.ofStart("um1", "a1", "原问题"));
    return new io.yak.ops.business.agent.domain.AgentTurnRecord(
        turnId, sessionId, userId, 1L, io.yak.ops.business.agent.domain.TurnKind.START, payload,
        io.yak.ops.business.agent.domain.TurnStatus.WAITING_INPUT, null, null, null, null, null);
  }



  private SessionRepository sessionRepository;
  private AgentTurnRepository turnRepository;
  private AgentTurnEventRepository turnEventRepository;
  private AgentTurnDispatcher dispatcher;
  private AgentChatService chatService;
  private MockedStatic<YakSecurityContext> securityContext;

  @BeforeEach
  void setUp() {
    sessionRepository = mock(SessionRepository.class);
    turnRepository = mock(AgentTurnRepository.class);
    turnEventRepository = mock(AgentTurnEventRepository.class);
    dispatcher = mock(AgentTurnDispatcher.class);
    chatService =
        new AgentChatService(
            new AgentSessionOwnerValidator(sessionRepository),
            mock(AgentStreamCoordinator.class),
            mock(AgentEventStreamTailer.class),
            dispatcher,
            new AgentTurnRegistry(),
            turnRepository,
            turnEventRepository,
            sessionRepository,
            mock(io.yak.ops.business.agent.runtime.AgentRuntime.class),
            () -> java.util.Optional.of(new io.yak.ops.core.project.ProjectContext(1L, "test-project")));
    securityContext = mockStatic(YakSecurityContext.class);
    securityContext.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
  }

  @AfterEach
  void tearDown() {
    securityContext.close();
  }

  @Test
  void submitOnBrandNewSessionBindsOwnershipWithFirstQuestionAsTitle() {
    when(turnRepository.hasActiveTurn("brand-new")).thenReturn(false);

    chatService.submitTurn("brand-new", "上个月各区域销售额是多少？");

    verify(sessionRepository)
        .insert(new SessionMeta("brand-new", 42L, 1L, "上个月各区域销售额是多少？", null, null));
    verify(turnRepository)
        .insertQueued(anyString(), eq("brand-new"), eq(42L), eq(1L), eq(TurnKind.START), anyString());
    verify(dispatcher).kick();
  }

  @Test
  void duplicateResumeConflictsAs409Semantics() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new io.yak.ops.business.agent.domain.SessionMeta("s1", 42L, 1L, null, null, null)));
    // 无 WAITING_INPUT 轮（重复 resolve 的典型形态）→ TurnConflictException（映射 409）
    when(turnRepository.latestWaitingTurnId("s1")).thenReturn(Optional.empty());
    org.junit.jupiter.api.Assertions.assertThrows(
        io.yak.ops.business.agent.conversation.TurnConflictException.class,
        () -> chatService.submitResume("s1",
            List.of(new io.yak.ops.business.agent.domain.ToolFeedback("c1", "t", "答"))));
  }

@Test
  void resumeCasConflictsLossToo() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new io.yak.ops.business.agent.domain.SessionMeta("s1", 42L, 1L, null, null, null)));
    when(turnRepository.latestWaitingTurnId("s1")).thenReturn(Optional.of("t9"));
    when(turnEventRepository.latestClarifyToolCallId("t9")).thenReturn(Optional.of("c1"));
    when(turnRepository.findByTurnId("t9")).thenReturn(Optional.of(waitingTurn("t9", "s1", 42L)));
    // CAS 失败：该轮已被并发应答/取消 → 同样 409 语义（toolCallId 校验已通过，走到 CAS）
    when(turnRepository.requeueForResume(eq("t9"), anyString())).thenReturn(false);
    org.junit.jupiter.api.Assertions.assertThrows(
        io.yak.ops.business.agent.conversation.TurnConflictException.class,
        () -> chatService.submitResume("s1",
            List.of(new io.yak.ops.business.agent.domain.ToolFeedback("c1", "t", "答"))));
  }

  @Test
  void resumeWithMismatchedToolCallIdIsRejected() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new io.yak.ops.business.agent.domain.SessionMeta("s1", 42L, 1L, null, null, null)));
    when(turnRepository.latestWaitingTurnId("s1")).thenReturn(Optional.of("t9"));
    when(turnEventRepository.latestClarifyToolCallId("t9")).thenReturn(Optional.of("c1"));
    // HITL 防伪造：feedback 的 toolCallId 必须匹配反问帧（DOMAIN 硬规则 7）
    org.junit.jupiter.api.Assertions.assertThrows(
        io.yak.ops.business.agent.conversation.TurnConflictException.class,
        () -> chatService.submitResume("s1",
            List.of(new io.yak.ops.business.agent.domain.ToolFeedback("forged-id", "t", "答"))));
    verify(turnRepository, never()).requeueForResume(anyString(), anyString());
    verify(dispatcher, never()).kick();
  }

  @Test
  void resumeWithMatchingToolCallIdProceedsToRequeue() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new io.yak.ops.business.agent.domain.SessionMeta("s1", 42L, 1L, null, null, null)));
    when(turnRepository.latestWaitingTurnId("s1")).thenReturn(Optional.of("t9"));
    when(turnEventRepository.latestClarifyToolCallId("t9")).thenReturn(Optional.of("c1"));
    when(turnRepository.findByTurnId("t9")).thenReturn(Optional.of(waitingTurn("t9", "s1", 42L)));
    when(turnRepository.requeueForResume(eq("t9"), anyString())).thenReturn(true);

    String turnId = chatService.submitResume("s1",
        List.of(new io.yak.ops.business.agent.domain.ToolFeedback("c1", "t", "答")));

    org.junit.jupiter.api.Assertions.assertEquals("t9", turnId);
    verify(turnRepository).requeueForResume(eq("t9"), anyString());
    verify(dispatcher).kick();
  }

  @Test
  void submitRejectedWhileSessionHasActiveTurn() {
    when(turnRepository.hasActiveTurn("busy")).thenReturn(true);

    try {
      chatService.submitTurn("busy", "第二个问题");
      org.junit.jupiter.api.Assertions.fail("expected busy rejection");
    } catch (IllegalStateException expected) {
      // 提交侧单飞：活跃轮次存在时直接拒绝
    }
    verify(turnRepository, never())
        .insertQueued(anyString(), anyString(), anyLong(), anyLong(), any(), any());
  }
}
