package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.toolset.CurrentDateInfoTool;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Compaction 超窗实测集成测试：压低 triggerMessages 驱动多轮大负载，
 * 验证上下文被摘要收敛、轮次正常完成、StateStore 出现压缩痕迹。
 *
 * <p>使用本地 fake OpenAI SSE 端点与 InMemory StateStore，不依赖真实模型与数据库。
 * fake 端点对所有 chat completion 请求返回固定文本响应（含 compaction 摘要请求）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompactionIntegrationTest {

  /** 测试用透传项目上下文作用域：不实际设置 ThreadLocal，仅直通动作。 */
  private static final class PassthroughProjectContextScope implements ProjectContextScope {
    @Override
    public <T> T call(ProjectContext context, java.util.function.Supplier<T> action) {
      return action.get();
    }
  }

  private static HttpServer server;
  private static AgentRuntime runtime;
  private static InMemoryAgentStateStore stateStore;
  private static final AtomicInteger requestCounter = new AtomicInteger();

  private static String sse(String json) {
    return "data: " + json.replace("\n", "") + "\n\n";
  }

  /** 返回固定文本响应：模拟模型回答（含 compaction 摘要场景，均返回简短文本）。 */
  private static byte[] textResponseStream(String content) {
    String chunk1 =
        sse(
            "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\","
                + "\"content\":\"" + content.replace("\"", "\\\"") + "\"}}]}");
    String finish =
        sse(
            "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}");
    return (chunk1 + finish + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
  }

  @BeforeAll
  static void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();

    // fake 端点：所有 chat completion 请求（含 compaction 摘要）返回简短文本
    for (String path : List.of("/v1/chat/completions", "/chat/completions")) {
      server.createContext(
          path,
          (HttpExchange exchange) -> {
            int count = requestCounter.incrementAndGet();
            byte[] body = textResponseStream("answer-round-" + count);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
    }
    server.start();

    stateStore = new InMemoryAgentStateStore();

    AgentProperties properties = new AgentProperties();
    properties.getModel().setBaseUrl("http://127.0.0.1:" + port + "/v1");
    properties.getModel().setApiKey("test");
    properties.getModel().setName("spike");
    properties.getChat().setMaxIters(1);
    // 压低 compaction 阈值以在测试中快速触发
    properties.getCompaction().setEnabled(true);
    properties.getCompaction().setTriggerMessages(4);
    properties.getCompaction().setKeepMessages(2);
    properties.getCompaction().setContextWindowSize(1000);
    properties.getCompaction().setReserved(200);

    io.yak.ops.business.agent.telemetry.AgentStepRecorder stepRecorder =
        mock(io.yak.ops.business.agent.telemetry.AgentStepRecorder.class);
    runtime =
        new AgentRuntime(
            List.of(new CurrentDateInfoTool()),
            List.of(),
            stateStore,
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
  }

  @AfterAll
  static void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void multipleTurnsTriggerCompactionAndCompleteNormally() throws Exception {
    String sessionId = "compaction-test-session";

    // 执行多轮对话，超过 triggerMessages=4 阈值以触发压缩
    for (int i = 0; i < 6; i++) {
      List<ChatTurnEvent> events = new CopyOnWriteArrayList<>();
      CountDownLatch done = new CountDownLatch(1);
      String turnId = "turn-compaction-" + i;

      runtime.stream(
          1L,
          sessionId,
          turnId,
          "问题 " + i + "：请分析第 " + i + " 轮数据",
          1L,
          events::add,
          done::countDown,
          error -> {
            events.add(ChatTurnEvent.error(String.valueOf(error)));
            done.countDown();
          });

      assertTrue(
          done.await(60, TimeUnit.SECONDS),
          "round " + i + " must finish within timeout");
      assertTrue(
          events.stream()
              .anyMatch(e -> e.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED),
          "round " + i + " must end with TURN_FINISHED, events: " + events);
    }

    // 验证 StateStore 中消息被压缩（消息数应小于总提交数）
    // 6 轮对话每轮至少产生 2 条消息（user + assistant），共 12+ 条；
    // compaction 后应被压缩到 keepMessages 附近
    var stateOpt =
        stateStore.get("1", sessionId, "agent_state", io.agentscope.core.state.AgentState.class);
    assertTrue(stateOpt.isPresent(), "StateStore should contain session state");
    int messageCount = stateOpt.get().getContext().size();
    // 压缩后消息数应远小于 12（6 轮 x 2 条/轮），但至少有 keepMessages 条
    assertTrue(
        messageCount < 12,
        "compaction should have reduced message count, actual: " + messageCount);
    assertTrue(
        messageCount >= 1,
        "state should not be empty after compaction, actual: " + messageCount);
  }
}
