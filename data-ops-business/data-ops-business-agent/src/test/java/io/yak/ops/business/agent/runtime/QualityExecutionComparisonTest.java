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
import io.yak.ops.business.quality.api.QualityExecutionComparisonQueryApi;
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
class QualityExecutionComparisonTest {
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

  private static GovernanceEvidenceTools tools(QualityExecutionComparisonQueryApi api) {
    UserExecutionScope scope = new UserExecutionScope() {
      @Override public <T> T call(long userId, long projectId, Supplier<T> action) {
        assertEquals(7, userId); assertEquals(42, projectId);
        return action.get();
      }
    };
    return new GovernanceEvidenceTools(new AgentToolExecution(scope, mock(ActionAuthorization.class)),
        new GovernanceEvidenceGateway(provider(null), provider(null), provider(api)));
  }

  private static GovernanceContextMiddleware middleware(GovernanceEvidenceTools tools) {
    return new GovernanceContextMiddleware(tools, mock(AgentObservationCollector.class), session -> "turn-quality");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PASSED", "NOT_PASSED", "ERROR", "NOT_RUN"})
  void actualSourceFactsRemainUnchangedAndGuardedAnswerMatchesOfficialHistory(String result) {
    var api = mock(QualityExecutionComparisonQueryApi.class);
    var before = new QualityExecutionComparisonQueryApi.Side("Q_base", 7L, "历史监控", "table", "SUCCESS", "NOT_PASSED", 1, 0, 1, 0, null, null,
        List.of(new QualityExecutionComparisonQueryApi.Rule(1L, "规则", "COUNT", "RULE", "field", "NOT_PASSED", "12", "0")), true);
    var after = new QualityExecutionComparisonQueryApi.Side("Q_old", 7L, "历史监控", "table", "SUCCESS", result, 1, 0, 0, 0, null, null,
        List.of(new QualityExecutionComparisonQueryApi.Rule(1L, "规则", "COUNT", "RULE", "field", result, "12", "20")), false);
    when(api.compare("Q_base", "Q_old")).thenReturn(new QualityExecutionComparisonQueryApi.Comparison(before, after,
        List.of(new QualityExecutionComparisonQueryApi.Alignment(1L, 0, 0, false))));
    var tools = tools(api);
    var context = context(pair("Q_base", "Q_old"));
    var execution = context.get(AgentExecutionContext.class);
    var preload = middleware(tools);
    String prompt = preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5));
    assertTrue(prompt.contains(QualityExecutionComparisonPrompt.INSTRUCTIONS));
    assertTrue(prompt.contains("\"truncated\":true"));
    assertTrue(prompt.contains("\"result\":\"" + result + "\""));

    var toolkit = new Toolkit(); toolkit.registerTool(tools);
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    var inference = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      String id = execution.evidence().entries().get(1).id();
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
    assertTrue(answer.contains(QualityExecutionComparisonPrompt.NEXT_STEP));
    assertTrue(answer.contains("/data-quality/execution/Q_old"));
    assertTrue(answer.contains("/data-quality/execution/Q_base"));
    assertEquals(3, execution.evidence().entries().size());
    assertFalse(answer.contains("example.invalid"));
    assertEquals(List.of(result, "12", "20"), execution.verifiedFacts().stream().map(fact -> fact.value()).toList());
    assertEquals(2, execution.toolBudget().usedCalls()); // one preload + one verification, not per prompt rebuild
    verify(api).compare("Q_base", "Q_old");
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", context.getSessionId(), "agent_state", AgentState.class)
        .orElseThrow().getContext()).getLast().content());
  }

  @ParameterizedTest
  @ValueSource(strings = {"denied", "unavailable", "missing"})
  void unreadableEvidenceCannotPublishModelDiagnosis(String mode) {
    var api = mock(QualityExecutionComparisonQueryApi.class);
    when(api.compare("Q_base", "Q_old")).thenThrow("denied".equals(mode) ? new SecurityException("private denial")
        : new IllegalStateException("private source diagnostics"));
    var tools = tools("missing".equals(mode) ? null : api);
    var context = context(pair("Q_base", "Q_old"));
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
    assertTrue(answer.contains(QualityExecutionComparisonPrompt.NEXT_STEP));
  }

  @Test void promptDoesNotPreloadSourcesForOrdinaryConversation() {
    var tools = mock(GovernanceEvidenceTools.class);
    assertEquals("base", middleware(tools).onSystemPrompt(null, context(null), "base").block());
    verifyNoInteractions(tools);
  }

  private static GovernanceTarget pair(String baseline, String current) {
    return new GovernanceTarget(null, current, null, null, null, null, null, null, null, baseline);
  }

  @Test void historicalTaskHitlKeepsTargetAndBudgetAndCannotGenerateNewRules() {
    var original = context(pair("Q_base", "Q_old"));
    var store = new InMemoryAgentStateStore();
    var config = new AgentProperties.Execution(); config.setMaxToolCalls(3);
    TurnToolBudgetState.attach(store, original, "turn-quality", false, config);
    original.get(AgentExecutionContext.class).reserveTool("get_quality_execution_comparison");
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool());
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var suspended = toolkit.callTools(List.of(ToolUseBlock.builder().id("clarify-quality").name("request_clarification")
        .input(Map.of("question", "关注哪条规则？")).content("{\"question\":\"关注哪条规则？\"}").build()), null, null, original).block().getFirst();
    assertTrue(suspended.isSuspended());
    var restored = context(pair("Q_base", "Q_old"));
    config.setMaxToolCalls(100);
    TurnToolBudgetState.attach(store, restored, "turn-quality", true, config);
    var execution = restored.get(AgentExecutionContext.class);
    assertEquals(2, execution.toolBudget().usedCalls()); assertEquals(3, execution.toolBudget().maxCalls());
    assertThrows(IllegalArgumentException.class, () -> execution.reserveTool("propose_quality_rules"));
    assertThrows(IllegalArgumentException.class, () -> TurnToolBudgetState.attach(store,
        context(pair("other", "Q_old")), "turn-quality", true, config));
    execution.reserveTool("get_quality_execution_comparison");
    assertThrows(IllegalStateException.class, () -> execution.reserveTool("verify_governance_facts"));
  }
}
