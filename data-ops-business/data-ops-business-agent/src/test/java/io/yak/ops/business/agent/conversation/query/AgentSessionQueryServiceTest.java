package io.yak.ops.business.agent.conversation.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.domain.HistoryTurn;
import io.yak.ops.business.agent.domain.HistoryTraceStep;
import io.yak.ops.business.agent.domain.HistoryTurnWithTrace;
import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.AgentStepRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.repository.support.TurnInputCodec;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import org.mockito.MockedStatic;

/** 历史回放行为测试：归属校验先于读取（DOMAIN 硬规则 4）。 */
class AgentSessionQueryServiceTest {

  private SessionRepository sessionRepository;
  private io.yak.ops.business.agent.repository.QueryLogRepository queryLogRepository;
  private AgentTurnRepository turnRepository;
  private AgentTurnEventRepository eventRepository;
  private AgentRuntime agentRuntime;
  private AgentSessionQueryService queryService;
  private MockedStatic<YakSecurityContext> securityContext;

  @BeforeEach
  void setUp() {
    sessionRepository = mock(SessionRepository.class);
    queryLogRepository = mock(io.yak.ops.business.agent.repository.QueryLogRepository.class);
    turnRepository = mock(AgentTurnRepository.class);
    eventRepository = mock(AgentTurnEventRepository.class);
    agentRuntime = mock(AgentRuntime.class);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(1L, "test-project"));
    queryService = new AgentSessionQueryService(
        sessionRepository,
        queryLogRepository,
        turnRepository,
        eventRepository,
        mock(AgentStepRepository.class),
        agentRuntime,
        new TraceViewAssembler(),
        currentProject);
    securityContext = mockStatic(YakSecurityContext.class);
    securityContext.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
  }

  @AfterEach
  void tearDown() {
    securityContext.close();
  }

  @Test
  void historyReadsStateStoreEvidenceAfterOwnershipCheck() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new SessionMeta("s1", 42L, 1L, null, null, null)));
    List<HistoryTurn> evidence = List.of(new HistoryTurn("user", "问"), new HistoryTurn("assistant", "答"));
    when(agentRuntime.history(42L, "s1")).thenReturn(evidence);
    when(turnRepository.listFailedBySession("s1")).thenReturn(List.of());
    when(turnRepository.listCompletedBySession("s1")).thenReturn(List.of());

    List<HistoryTurnWithTrace> result = queryService.history("s1");

    assertEquals(2, result.size());
    assertEquals("user", result.get(0).role());
    assertEquals("问", result.get(0).content());
    assertTrue(result.get(0).trace().isEmpty());
    assertEquals("assistant", result.get(1).role());
    assertEquals("答", result.get(1).content());
  }

  @Test
  void crossUserHistoryIsRejectedBeforeAnyRead() {
    when(sessionRepository.findBySessionId("s2"))
        .thenReturn(Optional.of(new SessionMeta("s2", 7L, 1L, null, null, null)));

    assertThrows(IllegalArgumentException.class, () -> queryService.history("s2"));
  }

  @Test
  void unknownSessionIsRejected() {
    when(sessionRepository.findBySessionId("ghost")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> queryService.history("ghost"));
  }

  private void ownSession() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new SessionMeta("s1", 42L, 1L, null, null, null)));
  }

  private AgentTurnRecord completed(String id) {
    return new AgentTurnRecord(id, "s1", 42L, 1L, TurnKind.START, "{}", TurnStatus.COMPLETED,
        null, null, null, null, null);
  }

  private List<HistoryTurn> group(String id, String text) {
    return List.of(new HistoryTurn("user", "相同问题", id), new HistoryTurn("assistant", text, id));
  }

  @Test
  void matchingUsesReferenceRatherThanCompletionOrderOrIdenticalText() {
    ownSession();
    when(agentRuntime.history(42L, "s1")).thenReturn(java.util.stream.Stream.of(
        group(null, "旧正文"), group("failed", "部分回答"), group("t2", "完成二"), group("t1", "完成一"))
        .flatMap(List::stream).toList());
    when(turnRepository.listCompletedBySession("s1")).thenReturn(List.of(completed("t1"), completed("t2")));
    when(turnRepository.listFailedBySession("s1")).thenReturn(List.of(new AgentTurnRecord(
        "failed", "s1", 42L, 1L, TurnKind.START, "{}", TurnStatus.FAILED,
        "GENERIC", "独立失败", null, null, null)));
    when(eventRepository.reconstructTrace("t1")).thenReturn(List.of(new HistoryTraceStep("think", "证据一", null, null, null)));
    when(eventRepository.reconstructTrace("t2")).thenReturn(List.of(new HistoryTraceStep("think", "证据二", null, null, null)));
    var result = queryService.history("s1");
    assertEquals(null, result.get(1).turnId());
    assertEquals(null, result.get(3).turnId());
    assertEquals("t2", result.get(5).turnId());
    assertEquals("证据二", result.get(5).trace().get(0).text());
    assertEquals("t1", result.get(7).turnId());
    assertEquals("证据一", result.get(7).trace().get(0).text());
    assertEquals("error", result.get(8).role());
    assertEquals("failed", result.get(8).turnId());
    verify(eventRepository, org.mockito.Mockito.never()).reconstructTrace("failed");
  }

  @Test
  void legacyTextNeverReadsAnUnrelatedCompletedTrace() {
    ownSession();
    when(agentRuntime.history(42L, "s1")).thenReturn(group(null, "原回答"));
    when(turnRepository.listCompletedBySession("s1")).thenReturn(List.of(completed("t1")));
    var result = queryService.history("s1");
    assertEquals("原回答", result.get(1).content());
    assertEquals(null, result.get(1).turnId());
    assertTrue(result.get(1).trace().isEmpty());
    verifyNoInteractions(eventRepository);
  }

  @Test
  void duplicateMessageGroupsAndDuplicateTurnRowsAreAmbiguous() {
    ownSession();
    when(agentRuntime.history(42L, "s1")).thenReturn(java.util.stream.Stream.of(
        group("t1", "一"), group("t1", "二"), group("t2", "三"))
        .flatMap(List::stream).toList());
    when(turnRepository.listCompletedBySession("s1")).thenReturn(List.of(completed("t1"), completed("t2"), completed("t2")));
    var result = queryService.history("s1");
    assertTrue(result.stream().allMatch(t -> t.turnId() == null && t.trace().isEmpty()));
    verifyNoInteractions(eventRepository);
  }

  @ParameterizedTest
  @EnumSource(value = TurnStatus.class, names = {"QUEUED", "RUNNING", "WAITING_INPUT", "FAILED", "CANCELLED", "INTERRUPTED"})
  void nonCompletedReferenceNeverExposesTraceEvenWhenReturnedInCompletionList(TurnStatus status) {
    ownSession();
    when(agentRuntime.history(42L, "s1")).thenReturn(group("t1", "部分回答"));
    when(turnRepository.listCompletedBySession("s1")).thenReturn(List.of(new AgentTurnRecord(
        "t1", "s1", 42L, 1L, TurnKind.START, "{}", status, null, null, null, null, null)));
    assertEquals(null, queryService.history("s1").get(1).turnId());
    verifyNoInteractions(eventRepository);
  }

  @Test
  void foreignTurnReferencesAndUnknownReferencesDoNotReadTrace() {
    ownSession();
    when(agentRuntime.history(42L, "s1")).thenReturn(java.util.stream.Stream.of(
        group("user", "一"), group("project", "二"), group("session", "三"), group("unknown", "四"))
        .flatMap(List::stream).toList());
    when(turnRepository.listCompletedBySession("s1")).thenReturn(List.of(
        new AgentTurnRecord("user", "s1", 7L, 1L, TurnKind.START, "{}", TurnStatus.COMPLETED, null, null, null, null, null),
        new AgentTurnRecord("project", "s1", 42L, 2L, TurnKind.START, "{}", TurnStatus.COMPLETED, null, null, null, null, null),
        new AgentTurnRecord("session", "s2", 42L, 1L, TurnKind.START, "{}", TurnStatus.COMPLETED, null, null, null, null, null)));
    assertTrue(queryService.history("s1").stream().allMatch(t -> t.turnId() == null));
    verifyNoInteractions(eventRepository);
  }

  @Test
  void foreignFailureCannotLeakItsTextIntoSessionHistory() {
    ownSession();
    when(turnRepository.listFailedBySession("s1")).thenReturn(List.of(new AgentTurnRecord(
        "foreign", "s2", 42L, 1L, TurnKind.START, "{}", TurnStatus.FAILED,
        "GENERIC", "private", null, null, null)));
    assertThrows(IllegalArgumentException.class, () -> queryService.history("s1"));
    verifyNoInteractions(eventRepository);
  }

  private AgentTurnRecord turn(TurnStatus status, GovernanceTarget target) {
    return new AgentTurnRecord("t1", "s1", 42L, 1L, TurnKind.START,
        TurnInputCodec.encode(TurnInput.ofStart("u1", "a1", "问题").withTarget(target)),
        status, null, null, null, null, null);
  }

  @Test
  void continuationRestoresPersistedTargetAndReadsOriginalDraftWithoutWriting() {
    ownSession();
    GovernanceTarget target = new GovernanceTarget(null, "Q_20261007");
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(TurnStatus.COMPLETED, target)));
    var view = queryService.continuation("s1");
    assertEquals(target, view.governanceTarget());
    assertEquals("t1", view.turnId());
    assertEquals(TurnStatus.COMPLETED, view.status());
    assertEquals(null, view.blockingReason());
    verify(agentRuntime).history(42L, "s1");
    verifyNoInteractions(eventRepository);
  }

  @Test
  void ordinaryLatestTurnDoesNotSearchOlderGovernanceTargets() {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(TurnStatus.COMPLETED, null)));
    assertEquals(null, queryService.continuation("s1").governanceTarget());
    verify(turnRepository).latestBySession("s1");
    verify(agentRuntime).history(42L, "s1");
    verifyNoInteractions(eventRepository);
  }

  @Test
  void legacySessionWithoutTurnsHasNoInventedContext() {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.empty());
    var view = queryService.continuation("s1");
    assertEquals(null, view.status());
    assertEquals(null, view.governanceTarget());
    assertEquals(null, view.blockingReason());
    verifyNoInteractions(agentRuntime, eventRepository);
  }

  @ParameterizedTest
  @EnumSource(value = TurnStatus.class, names = {"QUEUED", "RUNNING"})
  void activeTurnBlocksNewSubmissionButKeepsItsTarget(TurnStatus status) {
    ownSession();
    GovernanceTarget target = new GovernanceTarget(7L, null);
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(status, target)));
    var view = queryService.continuation("s1");
    assertEquals(status, view.status());
    assertEquals(target, view.governanceTarget());
    assertTrue(view.blockingReason().contains("排队或推理"));
    verifyNoInteractions(agentRuntime, eventRepository);
  }

  @Test
  void waitingTurnRestoresOnlyItsLatestQuestionProjection() {
    ownSession();
    var target = new GovernanceTarget(null, null, 9L, "QUALITY_RULES");
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(TurnStatus.WAITING_INPUT, target)));
    when(eventRepository.latestClarification("t1")).thenReturn(Optional.of(new ChatTurnEvent(
        ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED, "{\"question\":\"阈值？\",\"options\":[\"100\"]}",
        "clarify-2", "request_clarification", null, null, null)));
    var view = queryService.continuation("s1");
    assertEquals("clarify-2", view.clarification().toolCallId());
    assertTrue(view.clarification().question().contains("阈值"));
    assertEquals(target, view.governanceTarget());
    assertEquals(null, view.blockingReason());
    verifyNoInteractions(agentRuntime);
  }

  @Test
  void missingQuestionBlocksInsteadOfCreatingAnotherTurn() {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(TurnStatus.WAITING_INPUT, null)));
    when(eventRepository.latestClarification("t1")).thenReturn(Optional.empty());
    assertTrue(queryService.continuation("s1").blockingReason().contains("待答问题无法读取"));
    verifyNoInteractions(agentRuntime);
  }

  @Test
  void invalidQuestionToolCannotBeResumed() {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(TurnStatus.WAITING_INPUT, null)));
    when(eventRepository.latestClarification("t1")).thenReturn(Optional.of(new ChatTurnEvent(
        ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED, "问题", "c1", "run_dataset_query", null, null, null)));
    var view = queryService.continuation("s1");
    assertEquals(null, view.clarification());
    assertTrue(view.blockingReason() != null);
  }

  @Test
  void corruptPayloadIsBlockedWithoutExposingRawInput() {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(new AgentTurnRecord(
        "t1", "s1", 42L, 1L, TurnKind.START, "private malformed payload", TurnStatus.COMPLETED,
        null, null, null, null, null)));
    var view = queryService.continuation("s1");
    assertTrue(view.blockingReason().contains("任务上下文无法读取"));
    assertFalse(view.toString().contains("private malformed"));
    verifyNoInteractions(agentRuntime, eventRepository);
  }

  @ParameterizedTest
  @EnumSource(value = TurnStatus.class, names = {"FAILED", "CANCELLED", "INTERRUPTED"})
  void terminalOutcomeIsNotPretendedToBeResumable(TurnStatus status) {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(status, new GovernanceTarget(7L, null))));
    var view = queryService.continuation("s1");
    assertEquals(status, view.status());
    assertEquals(null, view.clarification());
    verify(agentRuntime).history(42L, "s1");
    verifyNoInteractions(eventRepository);
  }

  @Test
  void crossProjectSessionCannotReadHistoryContinuationOrObservability() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new SessionMeta("s1", 42L, 2L, null, null, null)));
    assertThrows(IllegalArgumentException.class, () -> queryService.continuation("s1"));
    assertThrows(IllegalArgumentException.class, () -> queryService.history("s1"));
    assertThrows(IllegalArgumentException.class, () -> queryService.sessionObservability("s1"));
    verifyNoInteractions(agentRuntime, turnRepository, eventRepository);
  }

  @Test
  void foreignUserAndMissingLoginRejectBeforeAnyTurnRead() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new SessionMeta("s1", 7L, 1L, null, null, null)));
    assertThrows(IllegalArgumentException.class, () -> queryService.continuation("s1"));
    securityContext.when(YakSecurityContext::getCurrentUserId).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> queryService.continuation("s1"));
    verifyNoInteractions(agentRuntime, turnRepository, eventRepository);
  }

  @Test
  void inconsistentTurnIdentityAndForeignProjectTraceAreRejected() {
    ownSession();
    var foreign = new AgentTurnRecord("t1", "other", 42L, 2L, TurnKind.START, "{}",
        TurnStatus.COMPLETED, null, null, null, null, null);
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(foreign));
    when(turnRepository.findByTurnId("t1")).thenReturn(Optional.of(foreign));
    assertThrows(IllegalArgumentException.class, () -> queryService.continuation("s1"));
    assertThrows(IllegalArgumentException.class, () -> queryService.turnTrace("t1"));
    verifyNoInteractions(agentRuntime, eventRepository);
  }

  @Test
  void startDraftUsesPersistedQuestionAndRejectsConflictingOrDuplicateOriginals() {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(turn(TurnStatus.CANCELLED, null)));
    when(agentRuntime.history(42L, "s1")).thenReturn(List.of());
    assertEquals("问题", queryService.continuation("s1").questionDraft());
    when(agentRuntime.history(42L, "s1")).thenReturn(List.of(new HistoryTurn("user", "别的问题", "t1")));
    assertEquals(null, queryService.continuation("s1").questionDraft());
    when(agentRuntime.history(42L, "s1")).thenReturn(List.of(
        new HistoryTurn("user", "问题", "t1"), new HistoryTurn("user", "问题", "t1")));
    assertEquals(null, queryService.continuation("s1").questionDraft());
    verifyNoInteractions(eventRepository);
  }

  @Test
  void resumeDraftRequiresUniqueOriginalReferenceAndNeverUsesFeedback() {
    ownSession();
    var input = TurnInput.ofResume("a1", List.of(new io.yak.ops.business.agent.domain.ToolFeedback("c1", "request_clarification", "反馈")));
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(new AgentTurnRecord(
        "t1", "s1", 42L, 1L, TurnKind.RESUME, TurnInputCodec.encode(input), TurnStatus.FAILED,
        "private error code", "secret api-key", null, null, null)));
    when(agentRuntime.history(42L, "s1")).thenReturn(List.of(new HistoryTurn("user", "原问题", "t1")));
    var view = queryService.continuation("s1");
    assertEquals("原问题", view.questionDraft());
    assertEquals("GENERIC", view.errorCode());
    assertFalse(view.toString().contains("secret"));
    when(agentRuntime.history(42L, "s1")).thenReturn(List.of(new HistoryTurn("user", "无引用旧问题")));
    assertEquals(null, queryService.continuation("s1").questionDraft());
    when(agentRuntime.history(42L, "s1")).thenThrow(new IllegalStateException("secret"));
    assertFalse(queryService.continuation("s1").draftUnavailableReason().contains("secret"));
  }

  @Test
  void oversizedOriginalIsUnavailableRatherThanTruncated() {
    ownSession();
    when(turnRepository.latestBySession("s1")).thenReturn(Optional.of(new AgentTurnRecord(
        "t1", "s1", 42L, 1L, TurnKind.START,
        TurnInputCodec.encode(TurnInput.ofStart("u", "a", "x".repeat(8001))), TurnStatus.COMPLETED,
        null, null, null, null, null)));
    when(agentRuntime.history(42L, "s1")).thenReturn(List.of());
    var view = queryService.continuation("s1");
    assertEquals(null, view.questionDraft());
    assertTrue(view.draftUnavailableReason() != null);
  }
}
