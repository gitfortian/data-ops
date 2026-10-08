package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.dao.mapper.AgentConfigMapper;
import io.yak.ops.business.agent.domain.*;
import io.yak.ops.business.agent.memory.*;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import io.yak.ops.business.agent.toolset.StandardMatchTools;
import io.yak.ops.core.project.ProjectContextScope;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Real SDK and HTTP protocol; the source tool is a fake, source permissions have independent tests. */
class StandardMatchRuntimeTest {
  private final ObjectMapper json = new ObjectMapper();
  private final StandardMatchTarget field = new StandardMatchTarget(7, "user_id", "BIGINT", "用户编号", "");
  private final GovernanceTarget target = new GovernanceTarget(null, null, null, "STANDARD_MATCH", field);

  @Test void syntheticOutputUsesScopedSkillBudgetAndValidatedHistory() throws Exception { scenario(false, false, 32, true); }
  @Test void nativeOutputUsesTheSameSourceAndHistoryGuards() throws Exception { scenario(true, false, 32, true); }
  @Test void syntheticOutputCannotBypassTurnCallBudget() throws Exception { scenario(false, false, 2, false); }
  @Test void unknownModelToolIsRejectedBeforeExecution() throws Exception { scenario(false, true, 32, false); }

  @Test void concurrentSessionsKeepTheirOwnSkillAndToolState() throws Exception { scenario(false, false, 32, true, true); }

  private void scenario(boolean nativeOutput, boolean unknown, int budget, boolean success) throws Exception {
    scenario(nativeOutput, unknown, budget, success, false);
  }
  @Test void providerRejectingNativeFormatFallsBackThroughSdkWithTheSameBudget() throws Exception {
    scenario(true, false, 32, true, false, true);
  }

  private void scenario(boolean nativeOutput, boolean unknown, int budget, boolean success, boolean concurrent) throws Exception {
    scenario(nativeOutput, unknown, budget, success, concurrent, false);
  }
  @Test void mappingUsesTheSameSdkStructuredDeliveryAndHistory() throws Exception { scenario(false, false, 32, true, false, false, true); }
  @Test void mappingNativeOutputKeepsSourceValidation() throws Exception { scenario(true, false, 32, true, false, false, true); }
  @Test void mappingSyntheticResponseRemainsBudgeted() throws Exception { scenario(false, false, 2, false, false, false, true); }

  private void scenario(boolean nativeOutput, boolean unknown, int budget, boolean success, boolean concurrent, boolean rejectNative) throws Exception {
    scenario(nativeOutput, unknown, budget, success, concurrent, rejectNative, false);
  }
  private void scenario(boolean nativeOutput, boolean unknown, int budget, boolean success, boolean concurrent, boolean rejectNative, boolean mapping) throws Exception {
    var mappingTarget = new ModelMappingTarget(7, "user_id", 9, "db", "users", "买家编号", "");
    var target = mapping ? new GovernanceTarget(null, null, null, "MODEL_MAPPING", null, mappingTarget) : this.target;
    String marker = mapping ? "yak-model-mapping" : "yak-standard-match";
    var skill = ScenarioSkillScopeTest.skill(mapping ? "model-field-mapping" : "standard-match", 1, "先核对类型与业务说明，再从目录选 ID；不匹配则反问。");
    var repository = mock(AgentSkillRepository.class);
    when(repository.getAllSkills()).thenReturn(List.of(skill, ScenarioSkillScopeTest.skill("unrelated", 1, "禁止泄漏的无关技能")));
    when(repository.getSource()).thenReturn("db:yak_agent_skill");
    var calls = new AtomicInteger();
    var requests = new CopyOnWriteArrayList<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      requests.add(request);
      calls.incrementAndGet();
      boolean nativeRequest = json.readTree(request).has("response_format");
      if (rejectNative && nativeRequest) {
        byte[] rejected = "{\"error\":{\"message\":\"unsupported response_format\",\"type\":\"invalid_request_error\"}}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(400, rejected.length);
        try (var output = exchange.getResponseBody()) { output.write(rejected); }
        return;
      }
      Map<String, Object> delta;
      String finish;
      boolean loaded = false;
      for (var message : json.readTree(request).path("messages")) {
        if ("tool".equals(message.path("role").asText())) loaded = true;
      }
      if (!loaded || unknown) {
        String name = unknown ? "analyze_with_python" : "load_skill_through_path";
        delta = Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0, "id", "load-1", "type", "function",
            "function", Map.of("name", name, "arguments", json.writeValueAsString(Map.of("skillId", skill.getSkillId(), "path", "SKILL.md"))))));
        finish = "tool_calls";
      } else {
        var proposal = mapping ? Map.of("candidates", List.of(Map.of("sourceColumn", "buyer_id", "reason", "业务编号")), "questions", List.of())
            : Map.of("candidates", List.of(Map.of("standardId", 9, "version", 2, "reason", "业务编号")), "questions", List.of());
        if (nativeRequest) {
          delta = Map.of("role", "assistant", "content", json.writeValueAsString(proposal)); finish = "stop";
        } else {
          delta = Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0, "id", "output-1", "type", "function",
              "function", Map.of("name", "generate_response", "arguments", json.writeValueAsString(Map.of("response", proposal)))))); finish = "tool_calls";
        }
      }
      String data = "data: " + json.writeValueAsString(Map.of("id", "c1", "model", "spike", "object", "chat.completion.chunk",
          "choices", List.of(Map.of("index", 0, "delta", delta)))) + "\n\n"
          + "data: " + json.writeValueAsString(Map.of("id", "c1", "model", "spike", "object", "chat.completion.chunk",
              "choices", List.of(Map.of("index", 0, "delta", Map.of(), "finish_reason", finish)))) + "\n\ndata: [DONE]\n\n";
      byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    try {
      var properties = new AgentProperties();
      properties.getModel().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
      properties.getModel().setApiKey("test"); properties.getModel().setName("spike");
      properties.getModel().setNativeStructuredOutput(nativeOutput);
      properties.getModel().setNativeStructuredOutputWithTools(nativeOutput);
      properties.getCompaction().setEnabled(false); properties.getExecution().setMaxToolCalls(budget);
      var store = new InMemoryAgentStateStore();
      var tools = mock(StandardMatchTools.class);
      when(tools.prepare(any(), eq(field))).thenReturn(new StandardMatchContext("用户", "a".repeat(64),
          List.of(new StandardMatchContext.TypeCandidate(9, 2, "user_id", "用户编号", "BIGINT", "")), false));
      when(tools.validate(any(), eq(field), any(), any(), eq(1), anyString())).thenAnswer(invocation -> {
        var proposal = invocation.getArgument(3, StandardMatchProposal.class);
        assertEquals(9L, proposal.candidates().getFirst().standardId());
        return new StandardMatchSuggestion("STANDARD_MATCH", field, "a".repeat(64), 1, invocation.getArgument(5), false,
            List.of(new StandardMatchSuggestion.Candidate(9, 2, "user_id", "源域权威名称", "BIGINT", proposal.candidates().getFirst().reason())), List.of());
      });
      var mappingTools = mock(io.yak.ops.business.agent.toolset.ModelMappingTools.class);
      when(mappingTools.prepare(any(), eq(mappingTarget))).thenReturn(new ModelMappingContext("用户", "MYSQL", "user_id", "BIGINT", "编号",
          "a".repeat(64), "b".repeat(64), List.of(new ModelMappingContext.SourceColumn("buyer_id", "BIGINT", "买家", false)), false));
      when(mappingTools.validate(any(), eq(mappingTarget), any(), any(), eq(1), anyString())).thenAnswer(invocation -> {
        var proposal = invocation.getArgument(3, ModelMappingProposal.class); assertEquals("buyer_id", proposal.candidates().getFirst().sourceColumn());
        return new ModelMappingSuggestion("MODEL_MAPPING", mappingTarget, "a".repeat(64), "b".repeat(64), "BIGINT", 1,
            invocation.getArgument(5), false, List.of(new ModelMappingSuggestion.Candidate("buyer_id", "BIGINT", false, "源域权威名称")), List.of());
      });
      List<io.yak.ops.business.agent.toolset.AgentToolBox> boxes = mapping ? List.of(mappingTools) : List.of(tools);
      var recorder = mock(AgentStepRecorder.class);
      var config = new AgentDynamicConfigService(mock(AgentConfigMapper.class));
      var runtime = new AgentRuntime(boxes, List.of(), store, properties, new AgentEventCodec(), recorder,
          new AgentObservationCollector(recorder, properties, config), config,
          mock(MemoryRecallService.class), mock(MemoryRepository.class), new ProjectContextScope() {
            @Override public <T> T call(io.yak.ops.core.project.ProjectContext context, java.util.function.Supplier<T> action) { return action.get(); }
          });
      runtime.setSkillRepository(repository);
      var events = new CopyOnWriteArrayList<ChatTurnEvent>();
      var error = new AtomicReference<Throwable>();
      var done = new CountDownLatch(1);
      var secondDone = new CountDownLatch(1);
      var secondError = new AtomicReference<Throwable>();
      if (concurrent) runtime.stream(2, "s2", "t2", "另一个字段任务", 2, target, e -> {}, secondDone::countDown,
          e -> { secondError.set(e); secondDone.countDown(); });
      runtime.stream(1, "s1", "t1", "匹配编号", 1, target, events::add, done::countDown, failure -> { error.set(failure); done.countDown(); });
      assertTrue(done.await(20, TimeUnit.SECONDS), "bounded fake SDK call");
      if (concurrent) {
        assertTrue(secondDone.await(20, TimeUnit.SECONDS)); assertNull(secondError.get(), String.valueOf(secondError.get()));
        assertTrue(runtime.history(2, "s2").stream().anyMatch(t -> t.content().contains(marker)));
        assertTrue(runtime.history(1, "s2").isEmpty());
      }
      if (success) {
        assertNull(error.get(), String.valueOf(error.get()));
        assertTrue(events.stream().anyMatch(e -> e.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED));
        var answer = runtime.history(1, "s1").stream().filter(t -> "assistant".equals(t.role())).findFirst().orElseThrow();
        assertTrue(answer.content().contains("源域权威名称"));
        assertTrue(answer.content().contains(marker));
        assertFalse(requests.getFirst().contains("禁止泄漏的无关技能"));
        var snapshot = store.get("1", "s1", TurnToolBudgetState.KEY, TurnToolBudgetState.class).orElseThrow();
        assertEquals(nativeOutput && !rejectNative ? 3 : 4, snapshot.budget().usedCalls());
        if (nativeOutput) assertTrue(requests.getFirst().contains("response_format"));
        else assertTrue(requests.getFirst().contains("generate_response"));
      } else {
        assertNotNull(error.get());
        verify(mappingTools, never()).validate(any(), any(), any(), any(), anyInt(), anyString());
        verify(tools, never()).validate(any(), any(), any(), any(), anyInt(), anyString());
        assertTrue(runtime.history(1, "s1").stream().noneMatch(t -> t.content().contains(marker)));
      }
    } finally { server.stop(0); }
  }
}
