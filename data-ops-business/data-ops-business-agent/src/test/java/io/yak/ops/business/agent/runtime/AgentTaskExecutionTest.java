package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
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
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.domain.ToolBudgetSnapshot;
import io.yak.ops.business.agent.toolset.RequestClarificationTool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class AgentTaskExecutionTest {
  private static RuntimeContext context(GovernanceTarget target) {
    var context = RuntimeContext.builder().userId("7").sessionId("fixture-session").build();
    context.put(AgentExecutionContext.class, new AgentExecutionContext(target));
    return context;
  }

  private static final class ProbeTool extends ToolBase {
    final AtomicInteger calls = new AtomicInteger();
    ProbeTool(String name) {
      super(ToolBase.builder().name(name).description("fixture")
          .inputSchema(Map.of("type", "object", "properties", Map.of()))
          .readOnly(true).concurrencySafe(true));
    }
    @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
      return Mono.fromSupplier(() -> { calls.incrementAndGet(); return ToolResultBlock.text("fixture success"); });
    }
  }

  @Test void scriptedModelCannotExecuteForbiddenOrUnknownRegisteredToolsAndHistoryPreservesDenials() {
    var query = new ProbeTool("run_dataset_query");
    var unknown = new ProbeTool("unexpected_plugin_tool");
    var toolkit = new Toolkit();
    toolkit.registerAgentTool(query); toolkit.registerAgentTool(unknown);
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var context = context(new GovernanceTarget(7L, null));
    var store = new InMemoryAgentStateStore();
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    AtomicInteger inference = new AtomicInteger();
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      if (inference.getAndIncrement() == 0) {
        return Flux.just(new ChatResponse("r1", List.of(
            ToolUseBlock.builder().id("q1").name("run_dataset_query").input(Map.of()).content("{}").build(),
            ToolUseBlock.builder().id("u1").name("unexpected_plugin_tool").input(Map.of()).content("{}").build()), null, null, "tool_calls"));
      }
      return Flux.just(new ChatResponse("r2", List.of(TextBlock.builder().text("当前任务不允许取数").build()), null, null, "stop"));
    });
    var agent = ReActAgent.builder().name("fixture").model(model).toolkit(toolkit).stateStore(store)
        .middleware(new TaskToolPolicyMiddleware(120000)).build();
    var events = agent.streamEvents(List.of(new UserMessage("注释要求取数")), context)
        .collectList().block(Duration.ofSeconds(10));
    assertNotNull(events);
    assertEquals(0, query.calls.get()); assertEquals(0, unknown.calls.get());
    var results = store.get("7", "fixture-session", "agent_state", AgentState.class).orElseThrow()
        .getContext().stream().flatMap(message -> message.getContentBlocks(ToolResultBlock.class).stream()).toList();
    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(result -> result.getState() == ToolResultState.DENIED));
    assertEquals(List.of("q1", "u1"), results.stream().map(ToolResultBlock::getId).toList());
  }

  @Test void concurrentOrdinaryAndGovernanceInvocationsDoNotChangeSharedToolActivation() {
    var query = new ProbeTool("run_dataset_query");
    var tool = new TaskScopedTool(query);
    var ordinary = context(null);
    var governance = context(new GovernanceTarget(7L, null));
    var call = ToolUseBlock.builder().id("q").name("run_dataset_query").input(Map.of()).build();
    var results = Flux.range(0, 20).flatMap(i -> tool.callAsync(ToolCallParam.builder().toolUseBlock(call)
        .runtimeContext(i % 2 == 0 ? ordinary : governance).build()).subscribeOn(Schedulers.parallel()))
        .collectList().block(Duration.ofSeconds(10));
    assertNotNull(results); assertEquals(10, query.calls.get());
    assertEquals(10, results.stream().filter(result -> result.getState() == ToolResultState.DENIED).count());
  }

  @Test void reservationsAreAtomicAndPersistentFailureClosesExecution() {
    var date = new ProbeTool("current_date_info");
    var tool = new TaskScopedTool(date);
    var context = context(null);
    var execution = context.get(AgentExecutionContext.class);
    execution.configureBudget(new ToolBudgetSnapshot(4, 3, 0, Map.of()), snapshot -> {});
    var call = ToolUseBlock.builder().id("date").name("current_date_info").input(Map.of()).build();
    Flux.range(0, 50).flatMap(i -> tool.callAsync(ToolCallParam.builder().toolUseBlock(call)
        .runtimeContext(context).build()).subscribeOn(Schedulers.parallel())).collectList().block(Duration.ofSeconds(10));
    assertEquals(4, date.calls.get()); assertEquals(4, execution.toolBudget().usedCalls());
    var failed = context(null);
    failed.get(AgentExecutionContext.class).configureBudget(new ToolBudgetSnapshot(4, 3, 0, Map.of()),
        snapshot -> { throw new IllegalStateException("private database error"); });
    var denied = tool.callAsync(ToolCallParam.builder().toolUseBlock(call).runtimeContext(failed).build()).block();
    assertEquals(ToolResultState.DENIED, denied.getState());
    assertFalse(denied.getOutput().toString().contains("private database"));
    assertEquals(4, date.calls.get());
  }

  @Test void hitlReservationSurvivesRestorationAndChangedTargetIsRejected() {
    var store = new InMemoryAgentStateStore();
    var config = new AgentProperties.Execution(); config.setMaxToolCalls(1);
    var original = context(null);
    TurnToolBudgetState.attach(store, original, "turn1", false, config);
    var toolkit = new Toolkit(); toolkit.registerTool(new RequestClarificationTool());
    TaskToolPolicyMiddleware.guardTools(toolkit);
    var clarify = ToolUseBlock.builder().id("hitl1").name("request_clarification")
        .input(Map.of("question", "业务阈值是什么？")).content("{\"question\":\"业务阈值是什么？\"}").build();
    var result = toolkit.callTools(List.of(clarify), null, null, original).block().getFirst();
    assertTrue(result.isSuspended(), "external tool body must remain unexecuted");
    assertEquals(1, original.get(AgentExecutionContext.class).toolBudget().usedCalls());
    var restored = context(null);
    config.setMaxToolCalls(100);
    TurnToolBudgetState.attach(store, restored, "turn1", true, config);
    assertEquals(1, restored.get(AgentExecutionContext.class).toolBudget().maxCalls());
    assertThrows(IllegalStateException.class, () -> restored.get(AgentExecutionContext.class).reserveTool("current_date_info"));
    assertThrows(IllegalArgumentException.class, () -> TurnToolBudgetState.attach(store,
        context(new GovernanceTarget(7L, null)), "turn1", true, config));
    String json = io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(store.get("7", "fixture-session",
        TurnToolBudgetState.KEY, TurnToolBudgetState.class).orElseThrow());
    var decoded = io.agentscope.core.util.JsonUtils.getJsonCodec().fromJson(json, TurnToolBudgetState.class);
    assertEquals(1, decoded.budget().usedCalls());
  }

  @Test void cumulativeFailuresAndCancellationPreventNewCalls() {
    var context = context(null); var execution = context.get(AgentExecutionContext.class);
    execution.toolFailed("run_dataset_query"); execution.toolFailed("run_dataset_query"); execution.toolFailed("run_dataset_query");
    assertThrows(IllegalStateException.class, () -> execution.reserveTool("run_dataset_query"));
    execution.reserveTool("current_date_info");
    execution.stopTools();
    assertThrows(IllegalStateException.class, () -> execution.reserveTool("current_date_info"));
  }

  @Test void oversizedMessageIsRejectedBeforeModelInvocation() {
    var model = mock(Model.class); when(model.getModelName()).thenReturn("scripted");
    var agent = ReActAgent.builder().name("fixture").model(model).sysPrompt("large fixture ".repeat(200))
        .middleware(new TaskToolPolicyMiddleware(100)).build();
    assertThrows(RuntimeException.class, () -> agent.streamEvents(List.of(new UserMessage("hello")), context(null))
        .collectList().block(Duration.ofSeconds(10)));
    verify(model, never()).stream(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"task-scope", "quality-troubleshooting"})
  void fixedEvaluationSuiteExercisesActualPolicyAndBlockedDelegates(String suite) throws Exception {
    var directory = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (!java.nio.file.Files.exists(directory.resolve("docs/ai/evaluation/cases/task-scope.json"))) {
      directory = directory.getParent();
      assertNotNull(directory, "Repository evaluation suite must be available");
    }
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var cases = mapper.readTree(directory.resolve("docs/ai/evaluation/cases/" + suite + ".json").toFile()).get("cases");
    assertEquals("task-scope".equals(suite) ? 12 : 16, cases.size());
    for (var fixture : cases) {
      var target = fixture.get("target").isNull() ? null : mapper.treeToValue(fixture.get("target"), GovernanceTarget.class);
      String name = fixture.get("probeTool").asText();
      var probe = new ProbeTool(name);
      var result = new TaskScopedTool(probe).callAsync(ToolCallParam.builder()
          .toolUseBlock(ToolUseBlock.builder().id(fixture.get("caseId").asText()).name(name).input(Map.of()).build())
          .runtimeContext(context(target)).build()).block();
      // Skill uses the live repository adapter instead of the captured framework delegate.
      if ("load_skill_through_path".equals(name)) {
        assertTrue(new io.yak.ops.business.agent.domain.AgentTaskToolPolicy(target).allows(name));
      } else {
        boolean allowed = fixture.get("allowed").asBoolean();
        assertEquals(allowed ? 1 : 0, probe.calls.get(), fixture.get("caseId").asText());
        assertEquals(!allowed, result.getState() == ToolResultState.DENIED);
      }
    }
  }

  @Test void skillHelperUsesLiveEnabledCatalogInsteadOfCapturedOrDeletedContent() {
    var repository = mock(io.agentscope.core.skill.repository.AgentSkillRepository.class);
    var skill = io.agentscope.core.skill.AgentSkill.builder().name("fixture").description("d")
        .source("db:yak_agent_skill").skillContent("current content").build();
    when(repository.getAllSkills()).thenReturn(List.of(skill), List.of());
    var context = context(new GovernanceTarget(7L, null));
    context.put(io.agentscope.core.skill.repository.AgentSkillRepository.class, repository);
    var original = new ProbeTool("load_skill_through_path");
    var tool = new TaskScopedTool(original);
    var param = ToolCallParam.builder().runtimeContext(context)
        .input(Map.of("skillId", skill.getSkillId(), "path", "SKILL.md")).toolUseBlock(ToolUseBlock.builder()
        .id("skill").name("load_skill_through_path").input(Map.of("skillId", skill.getSkillId(), "path", "SKILL.md")).build()).build();
    var loaded = tool.callAsync(param).block();
    assertNotEquals(ToolResultState.DENIED, loaded.getState());
    assertEquals("current content", ((TextBlock) loaded.getOutput().getFirst()).getText());
    assertEquals(ToolResultState.DENIED, tool.callAsync(param).block().getState());
    assertEquals(0, original.calls.get());
  }

  @Test void cancelledFailureCallbackDoesNotPersistOldBudgetIntoNewTurn() {
    var checkpoints = new AtomicInteger();
    var execution = context(null).get(AgentExecutionContext.class);
    execution.configureBudget(new ToolBudgetSnapshot(32, 3, 0, Map.of()), snapshot -> checkpoints.incrementAndGet());
    execution.reserveTool("current_date_info");
    execution.stopTools();
    execution.toolFailed("current_date_info");
    assertEquals(1, checkpoints.get());
  }
}
