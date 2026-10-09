package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import io.yak.ops.business.agent.AgentPermissionCode;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.AgentTaskToolPolicy;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.domain.ModelStructureReviewTarget;
import io.yak.ops.business.agent.gateway.GovernanceEvidenceGateway;
import io.yak.ops.business.agent.toolset.AgentToolExecution;
import io.yak.ops.business.agent.toolset.GovernanceEvidenceTools;
import io.yak.ops.business.agent.toolset.RequestClarificationTool;
import io.yak.ops.business.modeling.api.ModelStructureReviewQueryApi;
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

class ModelStructureReviewRuntimeTest {
  private static final String DEFINITION = "a".repeat(64);
  private static GovernanceTarget target(String definition) {
    return new GovernanceTarget(null, null, null, "MODEL_STRUCTURE_REVIEW", null, null, null, null, null, null, null,
        new ModelStructureReviewTarget("7", 3, definition));
  }
  private static RuntimeContext context(String definition) {
    var context = RuntimeContext.builder().userId("7").sessionId("structure-review").build();
    context.put(AgentExecutionContext.class, new AgentExecutionContext(target(definition)));
    context.put(AgentExecutionContext.PROJECT_ID, 42L); return context;
  }
  @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(value); return provider;
  }
  private static ModelStructureReviewQueryApi.Context result(String project) {
    return new ModelStructureReviewQueryApi.Context(project, "7", 3, "11", DEFINITION, 1, 2,
        List.of(new ModelStructureReviewQueryApi.Change("COLUMN", "amount", "INT", "BIGINT")),
        List.of(new ModelStructureReviewQueryApi.MappingCheck("amount", true, false, List.of("CHANGED_TARGET_REVIEW"))),
        List.of("NO_HISTORICAL_MAPPING_SNAPSHOT", "TYPE_COMPATIBILITY_NOT_CHECKED"));
  }

  @ParameterizedTest @ValueSource(strings = {"none", "source", "chat", "project"})
  void sdkPreloadVerificationDeliveryCheckAndOfficialHistoryAgree(String failure) {
    var api = mock(ModelStructureReviewQueryApi.class); var reads = new AtomicInteger();
    when(api.read(7, 3, DEFINITION)).thenAnswer(invocation -> {
      boolean delivery = reads.incrementAndGet() > 1;
      if (delivery && "source".equals(failure)) throw new IllegalArgumentException("saved inputs changed");
      return result(delivery && "project".equals(failure) ? "99" : "42");
    });
    UserExecutionScope scope = new UserExecutionScope() {
      @Override public <T> T call(long user, long project, Supplier<T> action) {
        assertEquals(7L, user); assertEquals(42L, project); return action.get();
      }
    };
    var authorization = mock(ActionAuthorization.class);
    var tools = new GovernanceEvidenceTools(new AgentToolExecution(scope, authorization),
        new GovernanceEvidenceGateway(provider(null), provider(null), provider(null), provider(null), provider(api)));
    var context = context(DEFINITION); var execution = context.get(AgentExecutionContext.class);
    var preload = new GovernanceContextMiddleware(tools, mock(AgentObservationCollector.class), session -> "turn");
    String prompt = preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5));
    assertTrue(prompt.contains(ModelStructureReviewPrompt.INSTRUCTIONS));
    assertEquals(prompt, preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5)));
    var toolkit = new Toolkit(); toolkit.registerTool(tools); TaskToolPolicyMiddleware.guardTools(toolkit);
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted"); var calls = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      String id = execution.evidence().entries().getFirst().id();
      if (calls.getAndIncrement() == 0) {
        var arguments = Map.<String, Object>of("fact_refs_json", "[{\"evidenceRef\":\"" + id + "\",\"field\":\"savedColumnCount\"}]");
        return Flux.just(new ChatResponse("verify", List.of(ToolUseBlock.builder().id("verify").name("verify_governance_facts")
            .input(arguments).content(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(arguments)).build()), null, null, "tool_calls"));
      }
      if ("chat".equals(failure)) doThrow(new SecurityException("revoked")).when(authorization).requirePermission(AgentPermissionCode.CHAT_RUN);
      return Flux.just(new ChatResponse("answer", List.of(TextBlock.builder().text("结构事实与当前映射检查 [" + id + "]").build()), null, null, "stop"));
    });
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().model(model).toolkit(toolkit).stateStore(store).middleware(preload)
        .middleware(new TaskToolPolicyMiddleware(120000)).build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("说明结构变更")), context), execution, context, agent,
        () -> tools.confirmStructureReview(context)).collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance).findFirst().orElseThrow()).getResult().getTextContent();
    if ("none".equals(failure)) {
      assertTrue(answer.contains(ModelStructureReviewPrompt.NEXT_STEP));
      assertTrue(answer.contains("/modeling/models/7?tab=version&reviewVersion=3")); assertTrue(answer.contains("yak-facts"));
    } else {
      assertTrue(answer.contains(ModelStructureReviewPrompt.INVALIDATED)); assertFalse(answer.contains("yak-facts"));
      assertFalse(answer.contains("结构事实与当前映射检查")); assertTrue(answer.contains("yak-evidence\n[]"));
    }
    assertFalse(answer.contains("yak-suggestion")); assertEquals(2, execution.toolBudget().usedCalls());
    assertEquals("chat".equals(failure) ? 1 : 2, reads.get());
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", context.getSessionId(), "agent_state", AgentState.class).orElseThrow().getContext()).getLast().content());
  }

  @Test void hitlKeepsDefinitionAndBudgetWhileOtherTasksCannotReadStructure() {
    var original = context(DEFINITION); var store = new InMemoryAgentStateStore();
    var config = new AgentProperties.Execution(); config.setMaxToolCalls(3);
    TurnToolBudgetState.attach(store, original, "turn", false, config);
    original.get(AgentExecutionContext.class).reserveTool("get_model_structure_review_evidence");
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool()); TaskToolPolicyMiddleware.guardTools(toolkit);
    var response = toolkit.callTools(List.of(ToolUseBlock.builder().id("clarify").name("request_clarification")
        .input(Map.of("question", "哪些变更需要人工确认？")).content("{\"question\":\"哪些变更需要人工确认？\"}").build()), null, null, original).block().getFirst();
    assertTrue(response.isSuspended());
    var restored = context(DEFINITION); config.setMaxToolCalls(100);
    TurnToolBudgetState.attach(store, restored, "turn", true, config);
    var execution = restored.get(AgentExecutionContext.class);
    assertEquals(2, execution.toolBudget().usedCalls()); assertEquals(3, execution.toolBudget().maxCalls());
    for (String tool : List.of("search_assets", "run_dataset_query", "get_consumer_version_impact_evidence", "save_analysis_report", "propose_quality_rules")) {
      assertThrows(IllegalArgumentException.class, () -> execution.reserveTool(tool));
    }
    assertThrows(IllegalArgumentException.class, () -> TurnToolBudgetState.attach(store, context("b".repeat(64)), "turn", true, config));
    assertFalse(new AgentTaskToolPolicy(null).allows("get_model_structure_review_evidence"));
    assertFalse(new AgentTaskToolPolicy(new GovernanceTarget(7L, null)).allows("get_model_structure_review_evidence"));
    execution.reserveTool("get_model_structure_review_evidence"); assertThrows(IllegalStateException.class, () -> execution.reserveTool("verify_governance_facts"));
  }

  @Test void targetRoundtripsLargeIdAndRejectsMixedOrInvalidSelection() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var large = new ModelStructureReviewTarget("9223372036854775807", 3, DEFINITION);
    assertEquals(large, json.readValue(json.writeValueAsString(large), ModelStructureReviewTarget.class));
    assertEquals(target(DEFINITION), json.readValue(json.writeValueAsString(target(DEFINITION)), GovernanceTarget.class));
    assertThrows(IllegalArgumentException.class, () -> new ModelStructureReviewTarget("01", 3, DEFINITION));
    assertThrows(IllegalArgumentException.class, () -> new ModelStructureReviewTarget("9223372036854775808", 3, DEFINITION));
    assertThrows(IllegalArgumentException.class, () -> new ModelStructureReviewTarget("7", 0, DEFINITION));
    assertThrows(IllegalArgumentException.class, () -> new ModelStructureReviewTarget("7", 3, "unprepared"));
    assertThrows(IllegalArgumentException.class, () -> new GovernanceTarget(7L, null, null, "MODEL_STRUCTURE_REVIEW", null, null, null, null, null, null, null, large));
  }
}
