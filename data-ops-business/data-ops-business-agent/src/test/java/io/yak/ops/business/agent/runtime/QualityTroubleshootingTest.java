package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.gateway.GovernanceEvidenceGateway;
import io.yak.ops.business.agent.toolset.AgentToolExecution;
import io.yak.ops.business.agent.toolset.GovernanceEvidenceTools;
import io.yak.ops.business.agent.toolset.RequestClarificationTool;
import io.yak.ops.business.quality.api.QualityEvidenceQueryApi;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.UserExecutionScope;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

/** SDK/source/guard contracts only; a scripted model is not semantic acceptance. */
class QualityTroubleshootingTest {
  private static RuntimeContext context(GovernanceTarget target) {
    var context = RuntimeContext.builder().userId("7").sessionId("quality-troubleshooting").build();
    context.put(AgentExecutionContext.class, new AgentExecutionContext(target));
    context.put(AgentExecutionContext.PROJECT_ID, 42L);
    return context;
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }

  private static GovernanceEvidenceTools tools(QualityEvidenceQueryApi api) {
    UserExecutionScope scope = new UserExecutionScope() {
      @Override public <T> T call(long userId, long projectId, Supplier<T> action) {
        assertEquals(7, userId); assertEquals(42, projectId);
        return action.get();
      }
    };
    return new GovernanceEvidenceTools(new AgentToolExecution(scope, mock(ActionAuthorization.class)),
        new GovernanceEvidenceGateway(provider(null), provider(api)));
  }

  private static GovernanceContextMiddleware middleware(GovernanceEvidenceTools tools) {
    return new GovernanceContextMiddleware(tools, mock(AgentObservationCollector.class), session -> "turn-quality");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PASSED", "NOT_PASSED", "ERROR", "NOT_RUN", "RUNNING"})
  void actualSourceFactsRemainUnchangedAndGuardedAnswerMatchesOfficialHistory(String result) {
    var api = mock(QualityEvidenceQueryApi.class);
    when(api.require("Q_old")).thenReturn(new QualityEvidenceQueryApi.ExecutionEvidence("Q_old", "历史监控", "table",
        "RUNNING".equals(result) ? "RUNNING" : "SUCCESS", result, 1, 0, 0, 0, null, null,
        List.of(new QualityEvidenceQueryApi.RuleEvidence(1L, "历史规则", "field", result, "12", "0", 1L, null)), true));
    var tools = tools(api);
    var context = context(new GovernanceTarget(null, "Q_old"));
    var execution = context.get(AgentExecutionContext.class);
    var preload = middleware(tools);
    String prompt = preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5));
    assertTrue(prompt.contains(QualityTroubleshootingPrompt.INSTRUCTIONS));
    assertTrue(prompt.contains("\"truncated\":true"));
    assertTrue(prompt.contains("\"result\":\"" + result + "\""));

    var toolkit = new Toolkit(); toolkit.registerTool(tools);
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    var inference = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      String id = execution.evidence().entries().getFirst().id();
      if (inference.getAndIncrement() == 0) {
        String refs = "[{\"evidenceRef\":\"" + id + "\",\"field\":\"rules[0].result\"},"
            + "{\"evidenceRef\":\"" + id + "\",\"field\":\"rules[0].metricValue\"},"
            + "{\"evidenceRef\":\"" + id + "\",\"field\":\"rules[0].expectedValue\"}]";
        Map<String, Object> arguments = Map.of("fact_refs_json", refs);
        return Flux.just(new ChatResponse("verify", List.of(ToolUseBlock.builder().id("verify-facts")
            .name("verify_governance_facts").input(arguments)
            .content(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(arguments)).build()), null, null, "tool_calls"));
      }
      return Flux.just(new ChatResponse("answer", List.of(TextBlock.builder().text("本次规则待核对 [" + id
          + "] [伪造监控](https://example.invalid/monitor/99)").build()), null, null, "stop"));
    });
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("quality").sysPrompt("base").model(model).toolkit(toolkit)
        .stateStore(store).middleware(preload).middleware(new TaskToolPolicyMiddleware(120000)).build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("排查本次执行")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance)
        .findFirst().orElseThrow()).getResult().getTextContent();
    assertTrue(answer.contains(QualityTroubleshootingPrompt.NEXT_STEP));
    assertTrue(answer.contains("/data-quality/execution/Q_old"));
    assertFalse(answer.contains("example.invalid"));
    assertEquals(List.of(result, "12", "0"), execution.verifiedFacts().stream().map(fact -> fact.value()).toList());
    assertEquals(2, execution.toolBudget().usedCalls()); // one preload + one verification, not per prompt rebuild
    verify(api).require("Q_old");
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", context.getSessionId(), "agent_state", AgentState.class)
        .orElseThrow().getContext()).getLast().content());
  }

  @ParameterizedTest
  @ValueSource(strings = {"denied", "unavailable", "missing"})
  void unreadableEvidenceCannotPublishModelDiagnosis(String mode) {
    var api = mock(QualityEvidenceQueryApi.class);
    when(api.require("Q_old")).thenThrow("denied".equals(mode) ? new SecurityException("private denial")
        : new IllegalStateException("private source diagnostics"));
    var tools = tools("missing".equals(mode) ? null : api);
    var context = context(new GovernanceTarget(null, "Q_old"));
    var execution = context.get(AgentExecutionContext.class);
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    when(model.stream(any(), any(), any())).thenReturn(Flux.just(new ChatResponse("answer",
        List.of(TextBlock.builder().text("已确认上游漏数").build()), null, null, "stop")));
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("quality").model(model).stateStore(store).middleware(middleware(tools)).build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("确认根因")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance)
        .findFirst().orElseThrow()).getResult().getTextContent();
    assertFalse(answer.contains("已确认上游漏数"));
    assertFalse(answer.contains("private"));
    assertTrue(answer.contains("没有可读取"));
    assertTrue(answer.contains("denied".equals(mode) ? "PERMISSION_DENIED" : "UNAVAILABLE"));
    assertTrue(answer.contains(QualityTroubleshootingPrompt.NEXT_STEP));
  }

  @Test void promptDoesNotPreloadSourcesForOrdinaryConversation() {
    var tools = mock(GovernanceEvidenceTools.class);
    assertEquals("base", middleware(tools).onSystemPrompt(null, context(null), "base").block());
    verifyNoInteractions(tools);
    for (var target : List.of(new GovernanceTarget(7L, null), new GovernanceTarget(7L, null, null, "ASSET_DESCRIPTION"),
        new GovernanceTarget(null, null, 9L, "QUALITY_RULES"))) {
      assertFalse(QualityTroubleshootingPrompt.appliesTo(target));
    }
  }

  @Test void historicalTaskHitlKeepsTargetAndBudgetAndCannotGenerateNewRules() {
    var original = context(new GovernanceTarget(null, "Q_old"));
    var store = new InMemoryAgentStateStore();
    var config = new AgentProperties.Execution(); config.setMaxToolCalls(3);
    TurnToolBudgetState.attach(store, original, "turn-quality", false, config);
    original.get(AgentExecutionContext.class).reserveTool("get_quality_execution_evidence");
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool());
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var suspended = toolkit.callTools(List.of(ToolUseBlock.builder().id("clarify-quality").name("request_clarification")
        .input(Map.of("question", "关注哪条规则？")).content("{\"question\":\"关注哪条规则？\"}").build()), null, null, original).block().getFirst();
    assertTrue(suspended.isSuspended());
    var restored = context(new GovernanceTarget(null, "Q_old"));
    config.setMaxToolCalls(100);
    TurnToolBudgetState.attach(store, restored, "turn-quality", true, config);
    var execution = restored.get(AgentExecutionContext.class);
    assertEquals(2, execution.toolBudget().usedCalls()); assertEquals(3, execution.toolBudget().maxCalls());
    assertThrows(IllegalArgumentException.class, () -> execution.reserveTool("propose_quality_rules"));
    assertThrows(IllegalArgumentException.class, () -> TurnToolBudgetState.attach(store,
        context(new GovernanceTarget(null, "Q_other")), "turn-quality", true, config));
    execution.reserveTool("get_quality_execution_evidence");
    assertThrows(IllegalStateException.class, () -> execution.reserveTool("verify_governance_facts"));
  }
}
