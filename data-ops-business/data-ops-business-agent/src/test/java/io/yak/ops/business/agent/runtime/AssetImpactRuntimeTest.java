package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
import io.yak.ops.business.asset.api.AssetGovernanceQueryApi;
import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
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
class AssetImpactRuntimeTest {
  private static RuntimeContext context(GovernanceTarget target) {
    var context = RuntimeContext.builder().userId("7").sessionId("asset-impact").build();
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

  private static GovernanceEvidenceTools tools(AssetGovernanceQueryApi api) {
    UserExecutionScope scope = new UserExecutionScope() {
      @Override public <T> T call(long userId, long projectId, Supplier<T> action) {
        assertEquals(7, userId); assertEquals(42, projectId);
        return action.get();
      }
    };
    return new GovernanceEvidenceTools(new AgentToolExecution(scope, mock(ActionAuthorization.class)),
        new GovernanceEvidenceGateway(provider(api), provider(null), provider(null)));
  }

  private static GovernanceContextMiddleware middleware(GovernanceEvidenceTools tools) {
    return new GovernanceContextMiddleware(tools, mock(AgentObservationCollector.class), session -> "turn-impact");
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void summaryIsPreloadedOnceAndVerifiedFinalMatchesOfficialHistory(boolean persistedConsumption) {
    var api = mock(AssetGovernanceQueryApi.class);
    var business = new java.util.LinkedHashMap<String, Object>();
    if (persistedConsumption) {
      business.putAll(Map.of("ownerDomain", "CONSUMING_DOMAINS", "status", "OK", "scope", "persisted windows", "successfulUsageCount", 2));
      business.put("activeSubscriptionCount", null);
      business.putAll(Map.of("subscriptionState", "UNAVAILABLE", "usageState", "READY", "subscriptionWindowLimit", 200,
          "usageWindowLimit", 2, "subscriptionWindowState", "UNKNOWN", "usageWindowState", "LIMIT_REACHED", "sourceReconciliation", "NOT_PERFORMED"));
    } else business.putAll(Map.of("ownerDomain", "METRIC", "status", "UNAVAILABLE", "scope", "private"));
    when(api.section(7, SectionType.USAGE)).thenReturn(new AssetSectionResult(SectionType.USAGE, SectionStatus.OK, "FEDERATED",
        new SectionMapSummary(Map.of(
            "pageActivity", Map.of("ownerDomain", "ASSET", "status", "OK", "windowDays", 30, "viewCount", 99),
            "structuralUsage", Map.of("ownerDomain", "LINEAGE", "status", "OK", "direction", "DOWNSTREAM", "hop", 1, "downstreamReferenceCount", 4),
            "businessConsumption", business)),
        null, null, List.of(), List.of(), null, null));
    var tools = tools(api);
    var context = context(new GovernanceTarget(7L, null, null, "ASSET_IMPACT"));
    var execution = context.get(AgentExecutionContext.class);
    var preload = middleware(tools);
    String prompt = preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5));
    assertTrue(prompt.contains(AssetImpactPrompt.INSTRUCTIONS));
    if (persistedConsumption) {
      assertTrue(prompt.contains("NOT_PERFORMED")); assertTrue(prompt.contains("LIMIT_REACHED"));
      assertTrue(prompt.contains("\"activeSubscriptionCount\":null"));
    }
    assertFalse(prompt.contains("private"));
    assertEquals(prompt, preload.onSystemPrompt(null, context, "base").block(Duration.ofSeconds(5)));

    var toolkit = new Toolkit(); toolkit.registerTool(tools);
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    var inference = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      String id = execution.evidence().entries().get(1).id();
      if (inference.getAndIncrement() == 0) {
        String refs = "[{\"evidenceRef\":\"" + id + "\",\"field\":\"downstreamReferenceCount\"},"
            + "{\"evidenceRef\":\"" + id + "\",\"field\":\"hop\"}"
            + (persistedConsumption ? ",{\"evidenceRef\":\"" + execution.evidence().entries().get(2).id()
                + "\",\"field\":\"usageWindowState\"}" : "") + "]";
        Map<String, Object> arguments = Map.of("fact_refs_json", refs);
        return Flux.just(new ChatResponse("verify", List.of(ToolUseBlock.builder().id("verify-facts")
            .name("verify_governance_facts").input(arguments)
            .content(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(arguments)).build()), null, null, "tool_calls"));
      }
      return Flux.just(new ChatResponse("answer", List.of(TextBlock.builder().text("下游结构关系待核对 [" + id
          + "] [伪造来源](https://example.invalid/monitor/99)").build()), null, null, "stop"));
    });
    var store = new InMemoryAgentStateStore();
    var agent = ReActAgent.builder().name("asset-impact").sysPrompt("base").model(model).toolkit(toolkit)
        .stateStore(store).middleware(preload).middleware(new TaskToolPolicyMiddleware(120000)).build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("说明所选资产影响")), context),
        execution, context, agent).collectList().block(Duration.ofSeconds(10));
    String answer = ((AgentResultEvent) events.stream().filter(AgentResultEvent.class::isInstance)
        .findFirst().orElseThrow()).getResult().getTextContent();
    assertTrue(answer.contains(AssetImpactPrompt.NEXT_STEP));
    assertTrue(answer.contains("/data-asset/detail/7"));
    assertFalse(answer.contains("yak-suggestion"));
    assertFalse(answer.contains("尚未生成"));
    assertEquals(3, execution.evidence().entries().size());
    assertFalse(answer.contains("example.invalid"));
    assertEquals(persistedConsumption ? List.of("4", "1", "LIMIT_REACHED") : List.of("4", "1"),
        execution.verifiedFacts().stream().map(fact -> fact.value()).toList());
    assertEquals(2, execution.toolBudget().usedCalls()); // one preload + one verification, not per prompt rebuild
    verify(api).section(7, SectionType.USAGE); verifyNoMoreInteractions(api);
    assertEquals(answer, AgentRuntime.projectHistory(store.get("7", context.getSessionId(), "agent_state", AgentState.class)
        .orElseThrow().getContext()).getLast().content());
  }

  @Test void hitlKeepsFixedAssetAndOriginalBudgetAndRejectsOtherReads() {
    var original = context(new GovernanceTarget(7L, null, null, "ASSET_IMPACT"));
    var store = new InMemoryAgentStateStore();
    var config = new AgentProperties.Execution(); config.setMaxToolCalls(3);
    TurnToolBudgetState.attach(store, original, "turn-impact", false, config);
    original.get(AgentExecutionContext.class).reserveTool("get_asset_impact_evidence");
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool());
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var suspended = toolkit.callTools(List.of(ToolUseBlock.builder().id("clarify-quality").name("request_clarification")
        .input(Map.of("question", "需要核对哪类影响？")).content("{\"question\":\"需要核对哪类影响？\"}").build()), null, null, original).block().getFirst();
    assertTrue(suspended.isSuspended());
    var restored = context(new GovernanceTarget(7L, null, null, "ASSET_IMPACT"));
    config.setMaxToolCalls(100);
    TurnToolBudgetState.attach(store, restored, "turn-impact", true, config);
    var execution = restored.get(AgentExecutionContext.class);
    assertEquals(2, execution.toolBudget().usedCalls()); assertEquals(3, execution.toolBudget().maxCalls());
    assertThrows(IllegalArgumentException.class, () -> execution.reserveTool("get_asset_section_evidence"));
    assertThrows(IllegalArgumentException.class, () -> TurnToolBudgetState.attach(store,
        context(new GovernanceTarget(8L, null, null, "ASSET_IMPACT")), "turn-impact", true, config));
    execution.reserveTool("get_asset_impact_evidence");
    assertThrows(IllegalStateException.class, () -> execution.reserveTool("verify_governance_facts"));
  }
}
