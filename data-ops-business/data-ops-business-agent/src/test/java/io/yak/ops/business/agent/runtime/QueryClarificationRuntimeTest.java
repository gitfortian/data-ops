package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.DatasetSummary;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.toolset.RequestClarificationTool;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class QueryClarificationRuntimeTest {
  private static RuntimeContext context(GovernanceTarget target) {
    var context = RuntimeContext.builder().userId("7").sessionId("query-clarify").build();
    context.put(AgentExecutionContext.class, new AgentExecutionContext(target));
    return context;
  }
  private static DatasetSummary.DatasetFields fields(int version) {
    return new DatasetSummary.DatasetFields(9, "支付", List.of(
        new DatasetSummary.FieldView("paid", "实付金额", "DECIMAL", "MEASURE", false, "源业务定义"),
        new DatasetSummary.FieldView("due", "应付金额", "DECIMAL", "MEASURE", false, "未扣优惠")), version, true);
  }
  private static Map<String, Object> args(String kind) {
    return Map.of("question", "统计实付还是应付？", "kind", kind, "dataset_id", 9L,
        "field_ids", List.of("paid", "due"), "options", List.of("AI编造金额"));
  }
  private static ToolUseBlock call(Map<String, Object> args) {
    return ToolUseBlock.builder().id("clarify1").name("request_clarification").input(args)
        .content(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(args)).build();
  }
  private static ToolResultBlock invoke(RuntimeContext context, Map<String, Object> args) {
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool());
    TaskToolPolicyMiddleware.guardTools(toolkit);
    return toolkit.callTools(List.of(call(args)), null, null, context).block(Duration.ofSeconds(10)).getFirst();
  }

  @ParameterizedTest @ValueSource(strings = {"FIELD", "TIME", "CALIBER"})
  void sourceOwnsFieldsAndOptionsAreOnlySuggestions(String kind) throws Exception {
    var context = context(null); var state = context.get(AgentExecutionContext.class); state.remember(fields(3));
    var projected = QueryClarificationProjection.prepare(call(args(kind)), state);
    var result = invoke(context, args(kind)); assertTrue(result.isSuspended(), result.getOutput().toString());
    var json = new ObjectMapper().readTree(projected.getContent());
    assertEquals("clarify1", projected.getId()); assertEquals(args(kind), projected.getInput());
    assertEquals(3, json.path("queryContext").path("versionNo").asInt());
    assertEquals("源业务定义", json.path("queryContext").path("fields").get(0).path("description").asText());
    assertEquals("FIELD".equals(kind) ? "实付金额（fieldId=paid）" : "AI编造金额", json.path("options").get(0).asText());
    assertEquals(1, state.toolBudget().usedCalls());
  }

  @Test void invalidAndOutOfScopeCallsNeverSuspendOrExposePrivateContent() {
    var inputs = new java.util.ArrayList<Map<String, Object>>();
    for (var replacement : List.of(Map.of("kind", "SQL"), Map.of("field_ids", List.of("missing", "due")),
        Map.of("field_ids", List.of("paid", "paid")), Map.of("dataset_id", 8),
        Map.of("question", "x".repeat(2049)), Map.of("options", List.of("x", "x")),
        Map.of("field_ids", List.of("paid")), Map.of("options", List.of("x".repeat(513))))) {
      var input = new HashMap<String, Object>(args("FIELD")); input.putAll(replacement); inputs.add(input);
    }
    for (var input : inputs) {
      var context = context(null); context.get(AgentExecutionContext.class).remember(fields(3));
      var result = invoke(context, input); assertEquals(ToolResultState.ERROR, result.getState()); assertFalse(result.isSuspended());
      assertFalse(result.getOutput().toString().contains("untrusted content"));
    }
    assertFalse(invoke(context(null), args("FIELD")).isSuspended());
    var governance = context(new GovernanceTarget(7L, null)); governance.get(AgentExecutionContext.class).remember(fields(3));
    assertFalse(invoke(governance, args("FIELD")).isSuspended());
    assertTrue(invoke(governance, Map.of("question", "请补充治理背景")).isSuspended());
  }

  @Test void sdkPendingAndResumeRetainProjectionAndRequireFreshDiscoveryBeforeQuery() {
    var store = new InMemoryAgentStateStore(); var toolkit = new Toolkit();
    var queries = new AtomicInteger(); var discoveries = new AtomicInteger();
    toolkit.registerTool(new RequestClarificationTool());
    toolkit.registerAgentTool(new ToolBase(ToolBase.builder().name("get_dataset_fields").description("fixture")
        .inputSchema(Map.of("type", "object", "properties", Map.of()))) {
      @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        param.getRuntimeContext().get(AgentExecutionContext.class).remember(fields(discoveries.incrementAndGet()));
        return Mono.just(ToolResultBlock.text("fields"));
      }
    });
    toolkit.registerAgentTool(new ToolBase(ToolBase.builder().name("run_dataset_query").description("fixture")
        .inputSchema(Map.of("type", "object", "properties", Map.of()))) {
      @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        try {
          var source = param.getRuntimeContext().get(AgentExecutionContext.class).requireDiscovery(9);
          queries.incrementAndGet(); return Mono.just(ToolResultBlock.text("query version=" + source.versionNo()));
        } catch (IllegalArgumentException missing) { return Mono.just(ToolResultBlock.text("rediscover").withState(ToolResultState.ERROR)); }
      }
    });
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted"); var step = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      int index = step.getAndIncrement();
      if (index == 5) return Flux.just(new ChatResponse("done", List.of(TextBlock.builder().text("已按回答取数").build()), null, null, "stop"));
      String name = index == 1 ? "request_clarification" : index == 0 || index == 3 ? "get_dataset_fields" : "run_dataset_query";
      var tool = index == 1 ? call(args("FIELD")) : ToolUseBlock.builder().id("c" + index).name(name).input(Map.of()).content("{}").build();
      List<ContentBlock> calls = index == 1 ? List.of(tool, ToolUseBlock.builder().id("parallel-query").name("run_dataset_query").input(Map.of()).content("{}").build()) : List.of(tool);
      return Flux.just(new ChatResponse("r" + index, calls, null, null, "tool_calls"));
    });
    var agent = ReActAgent.builder().name("query-fixture").model(model).toolkit(toolkit).stateStore(store)
        .middleware(new TaskToolPolicyMiddleware(120000)).build();
    var codec = new AgentEventCodec();
    var events = agent.streamEvents(List.of(new UserMessage("查金额")), context(null)).collectList().block(Duration.ofSeconds(10));
    assertNotNull(events);
    var pending = events.stream().map(codec::map).filter(java.util.Objects::nonNull)
        .filter(e -> e.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED).findFirst().orElseThrow();
    assertTrue(pending.delta().contains("queryContext")); assertEquals(0, queries.get());
    assertEquals("clarify1", pending.toolCallId());
    agent.streamEvents(List.of(new ToolResultMessage("clarify1", "request_clarification", "实付金额（fieldId=paid）")), context(null))
        .collectList().block(Duration.ofSeconds(10));
    assertEquals(2, discoveries.get()); assertEquals(1, queries.get());
    var messages = store.get("7", "query-clarify", "agent_state", AgentState.class).orElseThrow().getContext();
    assertTrue(messages.stream().flatMap(m -> m.getContentBlocks(ToolResultBlock.class).stream())
        .anyMatch(r -> r.getOutput().toString().contains("query version=2")));
  }

  @Test void multipleClarificationsInOneBatchCannotCreateAmbiguousPending() {
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool()); TaskToolPolicyMiddleware.guardTools(toolkit);
    var store = new InMemoryAgentStateStore(); var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    var steps = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> steps.getAndIncrement() == 0
        ? Flux.just(new ChatResponse("r1", List.of(call(Map.of("question", "确认一")),
            ToolUseBlock.builder().id("second").name("request_clarification").input(Map.of("question", "确认二")).content("{\"question\":\"确认二\"}").build()), null, null, "tool_calls"))
        : Flux.just(new ChatResponse("r2", List.of(TextBlock.builder().text("请合并问题").build()), null, null, "stop")));
    var agent = ReActAgent.builder().name("single-pending").model(model).toolkit(toolkit).stateStore(store)
        .middleware(new TaskToolPolicyMiddleware(120000)).build();
    var events = agent.streamEvents(List.of(new UserMessage("问数")), context(null)).collectList().block(Duration.ofSeconds(10));
    assertNotNull(events);
    var codec = new AgentEventCodec();
    assertFalse(events.stream().map(codec::map).filter(java.util.Objects::nonNull)
        .anyMatch(e -> e.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED));
    assertEquals(2, store.get("7", "query-clarify", "agent_state", AgentState.class).orElseThrow().getContext().stream()
        .flatMap(m -> m.getContentBlocks(ToolResultBlock.class).stream()).filter(r -> r.getState() == ToolResultState.ERROR).count());
  }

  @Test void oversizedSourceTextAndCombinedEnvelopeCannotBecomePending() {
    var bad = context(null);
    bad.get(AgentExecutionContext.class).remember(new DatasetSummary.DatasetFields(9, "源", List.of(
        new DatasetSummary.FieldView("paid", "实付", "DECIMAL", "MEASURE", false, "x".repeat(513)),
        fields(3).fields().get(1)), 3));
    assertFalse(invoke(bad, args("FIELD")).isSuspended());
    var bounded = context(null);
    var bigFields = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new DatasetSummary.FieldView(
        "f" + i, "n".repeat(256), "DECIMAL", "MEASURE", false, "d".repeat(512))).toList();
    bounded.get(AgentExecutionContext.class).remember(new DatasetSummary.DatasetFields(9, "源", bigFields, 3));
    var input = new HashMap<String, Object>(args("CALIBER")); input.put("question", "q".repeat(2048));
    input.put("field_ids", bigFields.stream().map(DatasetSummary.FieldView::fieldId).toList());
    input.put("options", java.util.stream.IntStream.range(0, 8).mapToObj(i -> "o".repeat(511) + i).toList());
    assertEquals(ToolResultState.ERROR, invoke(bounded, input).getState());
  }
}
