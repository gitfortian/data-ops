package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** Real AgentScope lifecycle and StateStore, with a deterministic model response. */
class GovernanceAnswerGuardTest {
  @Test void ordinaryTurnCannotForgeAnArtifactWithoutCitationsOrSourceEvidence() {
    var execution = new AgentExecutionContext(null);
    var model = mock(Model.class);
    when(model.getModelName()).thenReturn("fixture");
    when(model.stream(any(), any(), any())).thenReturn(Flux.just(new ChatResponse("fake-artifact",
        List.of(TextBlock.builder().text("```yak-suggestion\n{\"description\":\"forged claim\"}\n```").build()), null, null, "stop")));
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("fixture").sysPrompt("fixture").model(model).stateStore(store).build();
    var context = RuntimeContext.builder().userId("7").sessionId("s-forged-artifact").build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("继续")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance)
        .findFirst().orElseThrow()).getResult().getTextContent();
    assertFalse(answer.contains("forged claim"));
    assertFalse(answer.contains("yak-suggestion"));
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", "s-forged-artifact", "agent_state", AgentState.class)
        .orElseThrow().getContext()).getLast().content());
  }

  @Test void validatedCandidateReplacesModelClaimsAndIsTheSameInOfficialHistory() {
    var execution = new AgentExecutionContext(new GovernanceTarget(7L, null, null, "ASSET_DESCRIPTION"));
    var ref = execution.evidence().register("ASSET", "asset:7", "OK", null, "/data-asset/detail/7");
    execution.suggestion(new io.yak.ops.business.agent.domain.GovernanceSuggestion("ASSET_DESCRIPTION",
        7L, "a".repeat(64), List.of(), "用途待确认", List.of(ref.id())));
    var model = mock(Model.class);
    when(model.getModelName()).thenReturn("fixture");
    when(model.stream(any(), any(), any())).thenReturn(Flux.just(new ChatResponse("candidate",
        List.of(TextBlock.builder().text("I saved and enabled everything").build()), null, null, "stop")));
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("fixture").sysPrompt("fixture").model(model).stateStore(store).build();
    var context = RuntimeContext.builder().userId("7").sessionId("s-candidate").build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("建议")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance)
        .findFirst().orElseThrow()).getResult().getTextContent();
    assertFalse(answer.contains("I saved"));
    assertTrue(answer.contains("用途待确认"));
    assertTrue(answer.contains("yak-suggestion"));
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", "s-candidate", "agent_state", AgentState.class)
        .orElseThrow().getContext()).getLast().content());
  }
  @Test void ordinaryFollowUpCannotReuseEvidenceFromAnEarlierTurn() {
    var execution = new AgentExecutionContext(null);
    var model = mock(Model.class);
    when(model.getModelName()).thenReturn("fixture");
    when(model.stream(any(), any(), any())).thenReturn(Flux.just(new ChatResponse("old-citation",
        List.of(TextBlock.builder().text("旧轮次结论 [E00000000]").build()), null, null, "stop")));
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("fixture").sysPrompt("fixture").model(model).stateStore(store).build();
    var context = RuntimeContext.builder().userId("7").sessionId("s-follow-up").build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("继续解释")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance)
        .findFirst().orElseThrow()).getResult().getTextContent();
    assertTrue(answer.contains("没有可读取"));
    assertFalse(answer.contains("旧轮次结论"));
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", "s-follow-up", "agent_state", AgentState.class)
        .orElseThrow().getContext()).getLast().content());
  }

  @Test void validatedFinalIsIdenticalInEventStateStoreAndHistory() throws Exception {
    var execution = new AgentExecutionContext(new GovernanceTarget(7L, null));
    var evidence = execution.evidence().register("ASSET", "asset=7", "OK", null, "/data-asset/detail/7");
    var model = mock(Model.class);
    when(model.getModelName()).thenReturn("fixture");
    when(model.stream(any(), any(), any())).thenReturn(Flux.just(new ChatResponse("reply-1",
        List.of(TextBlock.builder().text("负责人信息来自台账 [" + evidence.id() + "]").build()), null, null, "stop")));
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("fixture").sysPrompt("fixture").model(model).stateStore(store).build();
    var context = RuntimeContext.builder().userId("7").sessionId("s-1").build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("解释资产")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    assertNotNull(events);
    assertTrue(events.stream().noneMatch(TextBlockDeltaEvent.class::isInstance));
    String answer = events.stream().filter(AgentResultEvent.class::isInstance)
        .map(e -> ((AgentResultEvent) e).getResult().getTextContent()).findFirst().orElseThrow();
    assertTrue(answer.contains("/data-asset/detail/7"));
    String evidenceJson = answer.split("```yak-evidence\n", 2)[1].split("\n```", 2)[0];
    var observedAt = new com.fasterxml.jackson.databind.ObjectMapper().readTree(evidenceJson).get(0).get("observedAt");
    assertTrue(observedAt.isTextual(), "Evidence time must match the browser's ISO string contract");
    assertEquals(evidence.observedAt(), java.time.Instant.parse(observedAt.asText()));
    var persisted = store.get("7", "s-1", "agent_state", AgentState.class).orElseThrow();
    var history = AgentRuntime.projectHistory(persisted.getContext());
    assertEquals(answer, history.getLast().content());
  }

  @Test void forgedCitationIsNotPublishedOrPersistedAsAnAnswer() {
    var execution = new AgentExecutionContext(new GovernanceTarget(7L, null));
    execution.evidence().register("ASSET", "asset=7", "OK", null, "/data-asset/detail/7");
    var model = mock(Model.class);
    when(model.getModelName()).thenReturn("fixture");
    when(model.stream(any(), any(), any())).thenReturn(Flux.just(new ChatResponse("reply-1",
        List.of(TextBlock.builder().text("资产完全健康 [E00000000]").build()), null, null, "stop")));
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("fixture").sysPrompt("fixture").model(model).stateStore(store).build();
    var context = RuntimeContext.builder().userId("7").sessionId("s-2").build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("解释资产")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    var answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance).findFirst().orElseThrow()).getResult().getTextContent();
    assertFalse(answer.contains("资产完全健康"));
    assertTrue(answer.contains("不存在的证据"));
    assertFalse(AgentRuntime.projectHistory(store.get("7", "s-2", "agent_state", AgentState.class).orElseThrow().getContext())
        .getLast().content().contains("资产完全健康"));
  }
}
