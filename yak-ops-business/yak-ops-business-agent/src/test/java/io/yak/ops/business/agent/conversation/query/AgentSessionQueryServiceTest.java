package io.yak.ops.business.agent.conversation.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
