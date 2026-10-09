package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
import io.yak.ops.business.agent.domain.AgentTaskToolPolicy;
import io.yak.ops.business.agent.domain.ConsumerVersionImpactTarget;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.gateway.GovernanceEvidenceGateway;
import io.yak.ops.business.agent.toolset.AgentToolExecution;
import io.yak.ops.business.agent.toolset.GovernanceEvidenceTools;
import io.yak.ops.business.agent.toolset.RequestClarificationTool;
import io.yak.ops.business.consumption.api.ConsumerVersionImpactQueryApi;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.UserExecutionScope;
import io.yak.ops.spi.section.SectionStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

class ConsumerVersionImpactRuntimeTest {
  private static GovernanceTarget target(String version) {
    return new GovernanceTarget(null, null, null, "CONSUMER_VERSION_IMPACT", null, null, null, null, null, null,
        new ConsumerVersionImpactTarget("DATASET", "101", version));
  }
  private static RuntimeContext context(String version) {
    var context = RuntimeContext.builder().userId("7").sessionId("consumer-version").build();
    context.put(AgentExecutionContext.class, new AgentExecutionContext(target(version)));
    context.put(AgentExecutionContext.PROJECT_ID, 42L); return context;
  }
  @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(value); return provider;
  }
  @Test void sdkPreloadVerificationAndOfficialHistoryKeepExactSourceScope() {
    var api = mock(ConsumerVersionImpactQueryApi.class);
    var empty = new ConsumerVersionImpactQueryApi.Window(SectionStatus.EMPTY, 0, "WITHIN_LIMIT", List.of());
    when(api.read("DATASET", "101", "9001")).thenReturn(new ConsumerVersionImpactQueryApi.Result(
        "DATASET", "101", "9001", SectionStatus.OK, "ACTIVE_SOURCE_REFERENCE", empty, empty));
    UserExecutionScope scope = new UserExecutionScope() {
      @Override public <T> T call(long user, long project, Supplier<T> action) {
        assertEquals(7L, user); assertEquals(42L, project); return action.get();
      }
    };
    var tools = new GovernanceEvidenceTools(new AgentToolExecution(scope, mock(ActionAuthorization.class)),
        new GovernanceEvidenceGateway(provider(null), provider(null), provider(null), provider(api)));
    var context = context("9001"); var execution = context.get(AgentExecutionContext.class);
    var preload = new GovernanceContextMiddleware(tools, mock(AgentObservationCollector.class), session -> "turn");
    String prompt = preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5));
    assertTrue(prompt.contains(ConsumerVersionImpactPrompt.INSTRUCTIONS));
    assertEquals(prompt, preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5)));
    var toolkit = new Toolkit(); toolkit.registerTool(tools); TaskToolPolicyMiddleware.guardTools(toolkit);
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    var calls = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      String id = execution.evidence().entries().get(1).id();
      if (calls.getAndIncrement() == 0) {
        var arguments = Map.<String, Object>of("fact_refs_json", "[{\"evidenceRef\":\"" + id + "\",\"field\":\"recordCount\"}]");
        return Flux.just(new ChatResponse("verify", List.of(ToolUseBlock.builder().id("verify").name("verify_governance_facts")
            .input(arguments).content(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(arguments)).build()), null, null, "tool_calls"));
      }
      return Flux.just(new ChatResponse("answer", List.of(TextBlock.builder().text("仅限选定版本的窗口 [" + id + "]").build()), null, null, "stop"));
    });
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("consumer-version").sysPrompt("base").model(model).toolkit(toolkit)
        .stateStore(store).middleware(preload).middleware(new TaskToolPolicyMiddleware(120000)).build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("说明消费影响")), context), execution, context, agent)
        .collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance).findFirst().orElseThrow()).getResult().getTextContent();
    assertTrue(answer.contains(ConsumerVersionImpactPrompt.NEXT_STEP));
    assertTrue(answer.contains("/data-analysis/consumption/DATASET%3A101?reviewVersion=9001"));
    assertFalse(answer.contains("yak-suggestion"));
    assertEquals(3, execution.evidence().entries().size()); assertEquals(2, execution.toolBudget().usedCalls());
    verify(api).read("DATASET", "101", "9001"); verifyNoMoreInteractions(api);
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", context.getSessionId(), "agent_state", AgentState.class).orElseThrow().getContext()).getLast().content());
  }
  @Test void hitlRestoresFixedVersionAndOriginalBudgetAndRejectsOtherTools() {
    var original = context("9001"); var store = new InMemoryAgentStateStore();
    var config = new AgentProperties.Execution(); config.setMaxToolCalls(3);
    TurnToolBudgetState.attach(store, original, "turn", false, config);
    original.get(AgentExecutionContext.class).reserveTool("get_consumer_version_impact_evidence");
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool()); TaskToolPolicyMiddleware.guardTools(toolkit);
    var response = toolkit.callTools(List.of(ToolUseBlock.builder().id("clarify").name("request_clarification")
        .input(Map.of("question", "核对哪类兼容性？")).content("{\"question\":\"核对哪类兼容性？\"}").build()), null, null, original).block().getFirst();
    assertTrue(response.isSuspended());
    var restored = context("9001"); config.setMaxToolCalls(100);
    TurnToolBudgetState.attach(store, restored, "turn", true, config);
    var execution = restored.get(AgentExecutionContext.class);
    assertEquals(2, execution.toolBudget().usedCalls()); assertEquals(3, execution.toolBudget().maxCalls());
    for (String tool : List.of("search_assets", "run_dataset_query", "get_asset_impact_evidence", "save_analysis_report", "propose_quality_rules")) {
      assertThrows(IllegalArgumentException.class, () -> execution.reserveTool(tool));
    }
    assertThrows(IllegalArgumentException.class, () -> TurnToolBudgetState.attach(store, context("9002"), "turn", true, config));
    assertFalse(new AgentTaskToolPolicy(null).allows("get_consumer_version_impact_evidence"));
    execution.reserveTool("get_consumer_version_impact_evidence");
    assertThrows(IllegalStateException.class, () -> execution.reserveTool("verify_governance_facts"));
  }
  @Test void serializedTargetRoundtripsLargeStringIdentitiesAndRejectsMixedScope() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var target = target("999999999999999999999999999999");
    assertEquals(target, json.readValue(json.writeValueAsString(target), GovernanceTarget.class));
    assertThrows(IllegalArgumentException.class, () -> new ConsumerVersionImpactTarget("DATASET", "01", "9"));
    assertThrows(IllegalArgumentException.class, () -> new GovernanceTarget(7L, null, null, "CONSUMER_VERSION_IMPACT", null, null, null, null, null, null, target.consumerVersionImpact()));
  }
}
