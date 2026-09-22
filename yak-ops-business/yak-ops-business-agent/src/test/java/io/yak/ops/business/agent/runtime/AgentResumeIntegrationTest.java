package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.ToolFeedback;
import io.yak.ops.business.agent.toolset.AgentToolBox;
import io.yak.ops.business.agent.toolset.CurrentDateInfoTool;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * HITL 全链路集成测试：外部工具挂起 -> CLARIFY_REQUESTED -> feedback 恢复 -> 终态。
 * 使用本地 fake OpenAI 端点与 InMemory StateStore，不依赖真实模型与数据库。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentResumeIntegrationTest {

  /** 测试用透传项目上下文作用域：不实际设置 ThreadLocal，仅直通动作。 */
  private static final class PassthroughProjectContextScope implements ProjectContextScope {
    @Override
    public <T> T call(ProjectContext context, java.util.function.Supplier<T> action) {
      return action.get();
    }
  }

  private static final String TOOL_CALL_RESPONSE =
      """
      {
        "id": "c1", "object": "chat.completion", "created": 1, "model": "spike",
        "choices": [{
          "index": 0,
          "message": {"role": "assistant", "content": null, "tool_calls": [{
            "id": "call_clarify_1", "type": "function",
            "function": {"name": "request_clarification", "arguments": "{\\"question\\": \\"请说明统计口径\\"}"}
          }]},
          "finish_reason": "tool_calls"
        }]
      }""";

  private static final String FINAL_RESPONSE =
      """
      {
        "id": "c2", "object": "chat.completion", "created": 2, "model": "spike",
        "choices": [{
          "index": 0,
          "message": {"role": "assistant", "content": "FINAL: 已按澄清后的口径完成分析。"},
          "finish_reason": "stop"
        }]
      }""";

  private static HttpServer server;
  private static AgentRuntime runtime;

  private static String sse(String json) {
    return "data: " + json.replace("\n", "") + "\n\n";
  }

  private static byte[] firstTurnStream() {
    String chunk1 =
        sse(
            "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{"
                + "\"index\":0,\"id\":\"call_clarify_1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"request_clarification\",\"arguments\":\"\"}}]}}]}");
    String args = "{\"question\": \"请说明统计口径\"}".replace("\"", "\\\"");
    String chunk2 =
        sse(
            "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"" + args + "\"}}]}}]}");
    String finish =
        sse(
            "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");
    return (chunk1 + chunk2 + finish + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] finalTurnStream() {
    String chunk1 =
        sse(
            "{\"id\":\"c2\",\"object\":\"chat.completion.chunk\",\"created\":2,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\","
                + "\"content\":\"FINAL: 已按澄清后的口径完成分析。\"}}]}");
    String finish =
        sse(
            "{\"id\":\"c2\",\"object\":\"chat.completion.chunk\",\"created\":2,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}");
    return (chunk1 + finish + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
  }

  public static class ClarifyTools implements AgentToolBox {

    @io.agentscope.core.tool.Tool(
        name = "request_clarification",
        description = "ask user for clarification",
        externalTool = true)
    public String requestClarification(
        @io.agentscope.core.tool.ToolParam(name = "question", description = "question") String question) {
      throw new IllegalStateException("外部工具不应在框架内执行");
    }
  }

  @BeforeAll
  static void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    AtomicInteger counter = new AtomicInteger();
    for (String path : List.of("/v1/chat/completions", "/chat/completions")) {
      server.createContext(
          path,
          (HttpExchange exchange) -> {
            boolean first = counter.incrementAndGet() == 1;
            byte[] body = first ? firstTurnStream() : finalTurnStream();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
    }
    server.start();

    AgentProperties properties = new AgentProperties();
    properties.getModel().setBaseUrl("http://127.0.0.1:" + port + "/v1");
    properties.getModel().setApiKey("test");
    properties.getModel().setName("spike");

    io.yak.ops.business.agent.telemetry.AgentStepRecorder stepRecorder =
        mock(io.yak.ops.business.agent.telemetry.AgentStepRecorder.class);
    runtime =
        new AgentRuntime(
            List.of(new ClarifyTools(), new CurrentDateInfoTool()),
            List.of(),
            new InMemoryAgentStateStore(),
            properties,
            new AgentEventCodec(),
            stepRecorder,
            new io.yak.ops.business.agent.runtime.AgentObservationCollector(
                stepRecorder,
                properties,
                new io.yak.ops.business.agent.repository.AgentDynamicConfigService(
                    mock(io.yak.ops.business.agent.dao.mapper.AgentConfigMapper.class))),
            new io.yak.ops.business.agent.repository.AgentDynamicConfigService(
                mock(io.yak.ops.business.agent.dao.mapper.AgentConfigMapper.class)),
            mock(io.yak.ops.business.agent.memory.MemoryRecallService.class),
            mock(io.yak.ops.business.agent.memory.MemoryRepository.class),
            new PassthroughProjectContextScope());
    // 不显式 assemble：与生产一致，验证懒组装路径（首次 stream 触发构建）
  }

  @AfterAll
  static void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @Order(1)
  void firstTurnSuspendsWithClarifyRequested() throws Exception {
    List<ChatTurnEvent> events = new CopyOnWriteArrayList<>();
    CountDownLatch done = new CountDownLatch(1);

    runtime.stream(
        1L,
        "resume-e2e",
        "turn-e2e-1",
        "请分析销售额",
        1L,
        events::add,
        done::countDown,
        error -> {
          events.add(ChatTurnEvent.error(String.valueOf(error)));
          done.countDown();
        });

    assertTrue(done.await(60, TimeUnit.SECONDS), "first turn must finish");
    assertTrue(
        events.stream().anyMatch(e -> e.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED),
        "events: " + events);
    ChatTurnEvent clarify =
        events.stream()
            .filter(e -> e.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED)
            .findFirst()
            .orElseThrow();
    assertEquals("call_clarify_1", clarify.toolCallId());
    assertEquals("request_clarification", clarify.toolName());
  }

  @Test
  @Order(2)
  void resumeWithFeedbackReachesFinalAnswer() throws Exception {
    List<ChatTurnEvent> events = new CopyOnWriteArrayList<>();
    CountDownLatch done = new CountDownLatch(1);

    runtime.resume(
        1L,
        "resume-e2e",
        "turn-e2e-1",
        List.of(new ToolFeedback("call_clarify_1", "request_clarification", "口径：已支付金额")),
        1L,
        events::add,
        done::countDown,
        error -> {
          events.add(ChatTurnEvent.error(String.valueOf(error)));
          done.countDown();
        });

    assertTrue(done.await(60, TimeUnit.SECONDS), "resumed turn must finish");
    assertTrue(
        events.stream()
            .anyMatch(
                e ->
                    e.type() == ChatTurnEvent.TurnEventType.TEXT_DELTA
                        && e.delta() != null
                        && e.delta().startsWith("FINAL")),
        "events: " + events);
    assertTrue(
        events.stream().anyMatch(e -> e.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED),
        "must end with TURN_FINISHED");
  }
}
