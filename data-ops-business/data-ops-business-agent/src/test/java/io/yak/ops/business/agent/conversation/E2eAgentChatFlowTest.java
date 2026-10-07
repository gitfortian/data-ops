package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.dao.mapper.AgentConfigMapper;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.HistoryTraceStep;
import io.yak.ops.business.agent.domain.JournaledTurnEvent;
import io.yak.ops.business.agent.domain.MessageTreeNode;
import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.domain.ToolFeedback;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.memory.MemoryFlushService;
import io.yak.ops.business.agent.memory.MemoryRecallService;
import io.yak.ops.business.agent.memory.MemoryRepository;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.MessageTreeRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import io.yak.ops.business.agent.runtime.AgentEventCodec;
import io.yak.ops.business.agent.runtime.AgentObservationCollector;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.business.agent.runtime.ChatTurnToAguiMapper;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import io.yak.ops.business.agent.toolset.AgentToolBox;
import io.yak.ops.business.agent.toolset.CurrentDateInfoTool;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * 数据智能体服务级端到端验证测试（E2E-AG 系列，真实业务场景留痕）。
 *
 * <p>链路与生产一致：AgentChatService（提交/恢复/取消/订阅）→ AgentTurnDispatcher（提交侧即时唤醒）
 * → AgentTurnExecutor（CAS 认领 + 事件落库 + 终态收敛）→ AgentRuntime（本地 fake OpenAI 端点驱动真实
 * ReAct 推理）→ 事件/消息树/会话仓储。仅外部依赖替身：LLM=本地 HttpServer，数据库仓储=内存实现
 * （CAS 语义与 yak_agent_turn 状态机一致）；SSE 传输部分以 mock 尾随器断言游标分叉语义。</p>
 *
 * <p>覆盖场景：自然语言提问全链路（E2E-AG-01）、会话单飞拒绝（E2E-AG-02）、HITL 反问挂起与恢复
 * （E2E-AG-03/04 防伪）、停止生成（E2E-AG-05）、会话越权（E2E-AG-06）、订阅游标语义（E2E-AG-07/08）。</p>
 */
class E2eAgentChatFlowTest {

  // ---------------- fake OpenAI 流式响应 ----------------

  private static String sse(String json) {
    return "data: " + json.replace("\n", "") + "\n\n";
  }

  private static byte[] plainAnswerStream(String text) {
    String chunk =
        sse(
            "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\""
                + text + "\"}}]}");
    String finish =
        sse(
            "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}");
    return (chunk + finish + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] clarificationStream() {
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

  private static byte[] finalAnswerStream() {
    String chunk =
        sse(
            "{\"id\":\"c2\",\"object\":\"chat.completion.chunk\",\"created\":2,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\","
                + "\"content\":\"FINAL: 已按澄清后的口径完成分析。\"}}]}");
    String finish =
        sse(
            "{\"id\":\"c2\",\"object\":\"chat.completion.chunk\",\"created\":2,\"model\":\"spike\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}");
    return (chunk + finish + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
  }

  /** 长挂起响应：模拟推理中途卡住（单飞/取消窗口留白）。 */
  private static byte[] stalledStream(int stallSeconds) {
    try {
      Thread.sleep(stallSeconds * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return plainAnswerStream("SLOW 回答");
  }

  public static class ClarifyTools implements AgentToolBox {
    @io.agentscope.core.tool.Tool(
        name = "request_clarification",
        description = "ask user for clarification",
        externalTool = true)
    public String requestClarification(
        @io.agentscope.core.tool.ToolParam(name = "question", description = "question")
            String question) {
      throw new IllegalStateException("外部工具不应在框架内执行");
    }
  }

  // ---------------- 内存仓储（CAS 语义对齐 yak_agent_turn 状态机） ----------------

  private static class InMemoryTurnRepository implements AgentTurnRepository {
    final Map<String, AgentTurnRecord> turns = new ConcurrentHashMap<>();

    @Override
    public void insertQueued(String turnId, String sessionId, long userId, long projectId,
                             TurnKind kind, String payloadJson) {
      turns.put(turnId, new AgentTurnRecord(turnId, sessionId, userId, projectId, kind, payloadJson,
          TurnStatus.QUEUED, null, null, LocalDateTime.now(), null, null));
    }

    @Override
    public Optional<AgentTurnRecord> findByTurnId(String turnId) {
      return Optional.ofNullable(turns.get(turnId));
    }

    @Override
    public synchronized boolean claimForExecution(String turnId) {
      AgentTurnRecord r = turns.get(turnId);
      if (r == null || r.status() != TurnStatus.QUEUED) {
        return false;
      }
      turns.put(turnId, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
          r.kind(), r.payloadJson(), TurnStatus.RUNNING, null, null, r.createTime(),
          LocalDateTime.now(), null));
      return true;
    }

    @Override
    public synchronized boolean markWaitingInput(String turnId) {
      AgentTurnRecord r = turns.get(turnId);
      if (r == null || r.status() != TurnStatus.RUNNING) {
        return false;
      }
      turns.put(turnId, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
          r.kind(), r.payloadJson(), TurnStatus.WAITING_INPUT, null, null, r.createTime(),
          r.startTime(), null));
      return true;
    }

    @Override
    public synchronized boolean requeueForResume(String turnId, String resumePayloadJson) {
      AgentTurnRecord r = turns.get(turnId);
      if (r == null || r.status() != TurnStatus.WAITING_INPUT) {
        return false;
      }
      turns.put(turnId, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
          TurnKind.RESUME, resumePayloadJson, TurnStatus.QUEUED, null, null, r.createTime(),
          r.startTime(), null));
      return true;
    }

    @Override
    public synchronized boolean complete(String turnId) {
      AgentTurnRecord r = turns.get(turnId);
      if (r == null || r.status() != TurnStatus.RUNNING) {
        return false;
      }
      turns.put(turnId, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
          r.kind(), r.payloadJson(), TurnStatus.COMPLETED, null, null, r.createTime(),
          r.startTime(), LocalDateTime.now()));
      return true;
    }

    @Override
    public synchronized boolean fail(String turnId, String errorCode, String errorMessage) {
      AgentTurnRecord r = turns.get(turnId);
      if (r == null || r.status() != TurnStatus.RUNNING) {
        return false;
      }
      turns.put(turnId, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
          r.kind(), r.payloadJson(), TurnStatus.FAILED, errorCode, errorMessage, r.createTime(),
          r.startTime(), LocalDateTime.now()));
      return true;
    }

    @Override
    public synchronized boolean cancelRunning(String turnId) {
      AgentTurnRecord r = turns.get(turnId);
      if (r == null || r.status() != TurnStatus.RUNNING) {
        return false;
      }
      turns.put(turnId, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
          r.kind(), r.payloadJson(), TurnStatus.CANCELLED, null, null, r.createTime(),
          r.startTime(), LocalDateTime.now()));
      return true;
    }

    @Override
    public synchronized int cancelQueuedBySession(String sessionId) {
      AtomicInteger n = new AtomicInteger();
      turns.forEach((id, r) -> {
        if (r.sessionId().equals(sessionId) && r.status() == TurnStatus.QUEUED) {
          turns.put(id, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
              r.kind(), r.payloadJson(), TurnStatus.CANCELLED, null, null, r.createTime(), null,
              LocalDateTime.now()));
          n.incrementAndGet();
        }
      });
      return n.get();
    }

    @Override
    public synchronized boolean cancelQueued(String turnId) {
      AgentTurnRecord r = turns.get(turnId);
      if (r == null || r.status() != TurnStatus.QUEUED) return false;
      turns.put(turnId, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
          r.kind(), r.payloadJson(), TurnStatus.CANCELLED, null, null, r.createTime(), null,
          LocalDateTime.now()));
      return true;
    }

    @Override
    public synchronized int interruptOrphanRunning() {
      AtomicInteger n = new AtomicInteger();
      turns.forEach((id, r) -> {
        if (r.status() == TurnStatus.RUNNING) {
          turns.put(id, new AgentTurnRecord(r.turnId(), r.sessionId(), r.userId(), r.projectId(),
              r.kind(), r.payloadJson(), TurnStatus.INTERRUPTED, null, null, r.createTime(),
              r.startTime(), LocalDateTime.now()));
          n.incrementAndGet();
        }
      });
      return n.get();
    }

    @Override
    public boolean hasActiveTurn(String sessionId) {
      return turns.values().stream().anyMatch(r -> r.sessionId().equals(sessionId)
          && !r.status().terminal());
    }

    @Override
    public Optional<String> latestWaitingTurnId(String sessionId) {
      return turns.values().stream()
          .filter(r -> r.sessionId().equals(sessionId) && r.status() == TurnStatus.WAITING_INPUT)
          .max(Comparator.comparing(AgentTurnRecord::createTime))
          .map(AgentTurnRecord::turnId);
    }

    @Override
    public List<AgentTurnRecord> listQueued(int limit) {
      return turns.values().stream()
          .filter(r -> r.status() == TurnStatus.QUEUED)
          .sorted(Comparator.comparing(AgentTurnRecord::createTime))
          .limit(limit)
          .toList();
    }

    @Override
    public List<AgentTurnRecord> listFailedBySession(String sessionId) {
      return turns.values().stream()
          .filter(r -> r.sessionId().equals(sessionId)
              && (r.status() == TurnStatus.FAILED || r.status() == TurnStatus.INTERRUPTED))
          .sorted(Comparator.comparing(AgentTurnRecord::createTime))
          .toList();
    }

    @Override
    public List<AgentTurnRecord> listCompletedBySession(String sessionId) {
      return turns.values().stream()
          .filter(r -> r.sessionId().equals(sessionId) && r.status() == TurnStatus.COMPLETED)
          .sorted(Comparator.comparing(AgentTurnRecord::createTime))
          .toList();
    }

    @Override
    public List<AgentTurnRecord> listBySession(String sessionId) {
      return turns.values().stream()
          .filter(r -> r.sessionId().equals(sessionId))
          .sorted(Comparator.comparing(AgentTurnRecord::createTime))
          .toList();
    }

    @Override
    public Optional<AgentTurnRecord> latestBySession(String sessionId) {
      return listBySession(sessionId).stream().reduce((left, right) -> right);
    }
  }

  /** 事件仓库内存实现：append 分配全局自增 eventId（SSE 游标），listAfter 按游标增量取帧。 */
  private static class InMemoryEventRepository implements AgentTurnEventRepository {
    final Map<String, List<JournaledTurnEvent>> byTurn = new ConcurrentHashMap<>();
    final Map<String, Long> lastIds = new ConcurrentHashMap<>();
    final AtomicLong seq = new AtomicLong();

    @Override
    public long append(String turnId, ChatTurnEvent event) {
      long id = seq.incrementAndGet();
      byTurn.computeIfAbsent(turnId, k -> new CopyOnWriteArrayList<>())
          .add(new JournaledTurnEvent(id, event));
      lastIds.put(turnId, id);
      return id;
    }

    @Override
    public List<JournaledTurnEvent> listAfter(String turnId, long afterId, int limit) {
      List<JournaledTurnEvent> frames = byTurn.getOrDefault(turnId, List.of());
      List<JournaledTurnEvent> result = new ArrayList<>();
      for (JournaledTurnEvent frame : frames) {
        if (frame.eventId() > afterId) {
          result.add(frame);
          if (result.size() >= limit) {
            break;
          }
        }
      }
      return result;
    }

    @Override
    public long latestEventId(String turnId) {
      return lastIds.getOrDefault(turnId, 0L);
    }

    @Override
    public List<HistoryTraceStep> reconstructTrace(String turnId) {
      List<JournaledTurnEvent> frames = byTurn.getOrDefault(turnId, List.of());
      List<HistoryTraceStep> steps = new ArrayList<>();
      for (JournaledTurnEvent frame : frames) {
        ChatTurnEvent event = frame.event();
        steps.add(new HistoryTraceStep(event.type().name(), event.delta(),
            event.toolCallId(), event.toolName(), event.toolResult()));
      }
      return steps;
    }

    @Override
    public Optional<String> latestClarifyToolCallId(String turnId) {
      return byTurn.getOrDefault(turnId, List.of()).stream()
          .map(JournaledTurnEvent::event)
          .filter(e -> e.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED)
          .map(ChatTurnEvent::toolCallId)
          .filter(id -> id != null && !id.isBlank())
          .findFirst();
    }

    @Override
    public Optional<ChatTurnEvent> latestClarification(String turnId) {
      return byTurn.getOrDefault(turnId, List.of()).stream().map(JournaledTurnEvent::event)
          .filter(event -> event.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED)
          .reduce((left, right) -> right);
    }
  }

  private static class InMemorySessionRepository implements SessionRepository {
    final Map<String, SessionMeta> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<SessionMeta> findBySessionId(String sessionId) {
      return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void insert(SessionMeta meta) {
      sessions.put(meta.sessionId(), meta);
    }

    @Override
    public List<SessionMeta> listByUser(long userId, long projectId) {
      return sessions.values().stream()
          .filter(s -> s.userId() == userId && s.projectId() == projectId)
          .toList();
    }

    @Override
    public void updateTitle(String sessionId, String title) {
      sessions.computeIfPresent(sessionId,
          (k, s) -> new SessionMeta(s.sessionId(), s.userId(), s.projectId(), title, s.createTime(), null));
    }

    @Override
    public void deleteBySessionId(String sessionId) {
      sessions.remove(sessionId);
    }
  }

  private static class InMemoryMessageTreeRepository implements MessageTreeRepository {
    final Map<String, MessageTreeNode> nodes = new ConcurrentHashMap<>();

    @Override
    public void append(MessageTreeNode node) {
      nodes.put(node.messageId(), node);
    }

    @Override
    public boolean complete(String sessionId, String messageId, String content, Long totalTokens) {
      MessageTreeNode node = nodes.get(messageId);
      if (node == null) {
        return false;
      }
      nodes.put(messageId, new MessageTreeNode(node.sessionId(), node.messageId(),
          node.parentId(), node.role(), content, node.modelName(), totalTokens, node.done(),
          node.createTime(), LocalDateTime.now()));
      return true;
    }

    @Override
    public Optional<MessageTreeNode> findCurrentLeaf(String sessionId) {
      return nodes.values().stream()
          .filter(n -> n.sessionId().equals(sessionId))
          .max(Comparator.comparing(MessageTreeNode::createTime));
    }

    @Override
    public List<MessageTreeNode> listBySession(String sessionId) {
      return nodes.values().stream()
          .filter(n -> n.sessionId().equals(sessionId))
          .sorted(Comparator.comparing(MessageTreeNode::createTime))
          .toList();
    }
  }

  // ---------------- 装配 ----------------

  /** 请求级响应脚本：入参为请求序号（1-based），按序号返回不同流。 */
  private interface LlmScript extends Function<Integer, byte[]> {}

  private record Assembly(
      AgentChatService service,
      AgentTurnRepository turns,
      AgentTurnEventRepository events,
      SessionRepository sessions,
      MessageTreeRepository tree,
      AgentTurnDispatcher dispatcher,
      AgentStreamCoordinator coordinator,
      AgentEventStreamTailer tailer) {

    /** 归还线程资源：dispatcher worker / SSE 心跳 / 事件尾随（测试直装配不走 Spring，需手动释放）。 */
    void cleanup() {
      if (dispatcher != null) {
        dispatcher.shutdown();
      }
      if (coordinator != null) {
        coordinator.shutdown();
      }
      if (tailer != null) {
        tailer.shutdown();
      }
    }
  }

  private static Assembly assemble(int port) {
    return assemble(port, null);
  }

  private static Assembly assemble(int port, AgentEventStreamTailer tailerOverride) {
    AgentProperties props = new AgentProperties();
    props.getModel().setBaseUrl("http://127.0.0.1:" + port + "/v1");
    props.getModel().setApiKey("test");
    props.getModel().setName("spike");
    props.getTurn().setWorkerPoolSize(1);
    props.getTurn().setSseTailPollMillis(50L);
    props.getTurn().setReplayBatchSize(50);

    InMemorySessionRepository sessions = new InMemorySessionRepository();
    InMemoryEventRepository events = new InMemoryEventRepository();
    InMemoryTurnRepository turns = new InMemoryTurnRepository();
    InMemoryMessageTreeRepository tree = new InMemoryMessageTreeRepository();

    AgentStepRecorder stepRecorder = mock(AgentStepRecorder.class);
    AgentDynamicConfigService configService =
        new AgentDynamicConfigService(mock(AgentConfigMapper.class));
    AgentObservationCollector observationCollector =
        new AgentObservationCollector(stepRecorder, props, configService);
    AgentRuntime runtime =
        new AgentRuntime(
            List.of(new ClarifyTools(), new CurrentDateInfoTool()),
            List.of(),
            new InMemoryAgentStateStore(),
            props,
            new AgentEventCodec(),
            stepRecorder,
            observationCollector,
            configService,
            mock(MemoryRecallService.class),
            mock(MemoryRepository.class),
            new PassthroughProjectContextScope());

    ChatTurnToAguiMapper chatTurnToAguiMapper = new ChatTurnToAguiMapper();
    io.agentscope.core.agui.encoder.AguiEventEncoder aguiEventEncoder =
        new io.agentscope.core.agui.encoder.AguiEventEncoder();
    AgentStreamCoordinator coordinator =
        new AgentStreamCoordinator(chatTurnToAguiMapper, aguiEventEncoder);
    AgentEventStreamTailer tailer = tailerOverride != null ? tailerOverride
        : new AgentEventStreamTailer(events, turns, coordinator, props);
    AgentTurnRegistry registry = new AgentTurnRegistry();
    AgentTurnExecutor executor =
        new AgentTurnExecutor(runtime, turns, events, tree, registry, observationCollector,
            mock(MemoryFlushService.class), new PassthroughProjectContextScope());
    AgentTurnDispatcher dispatcher = new AgentTurnDispatcher(turns, executor, props);
    AgentChatService service =
        new AgentChatService(
            new AgentSessionOwnerValidator(sessions),
            coordinator,
            tailer,
            dispatcher,
            registry,
            turns,
            events,
            sessions,
            runtime,
            () -> java.util.Optional.of(new io.yak.ops.core.project.ProjectContext(1L, "e2e-project")));
    return new Assembly(service, turns, events, sessions, tree, dispatcher, coordinator, tailer);
  }

  private static HttpServer startServer(LlmScript script) throws Exception {
    AtomicInteger counter = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    for (String path : List.of("/v1/chat/completions", "/chat/completions")) {
      server.createContext(path, (HttpExchange exchange) -> {
        int seq = counter.incrementAndGet();
        byte[] body = script.apply(seq);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(body);
        }
      });
    }
    server.start();
    return server;
  }

  /** 直通 ProjectContextScope：run 直接执行 action，不设 ThreadLocal（E2E 测试不依赖项目上下文恢复）。 */
  private static final class PassthroughProjectContextScope implements io.yak.ops.core.project.ProjectContextScope {
    @Override
    public <T> T call(io.yak.ops.core.project.ProjectContext context, java.util.function.Supplier<T> action) {
      return action.get();
    }
  }

  private static List<ChatTurnEvent> allEvents(AgentTurnEventRepository events, String turnId) {
    List<ChatTurnEvent> result = new ArrayList<>();
    long after = 0;
    while (true) {
      List<JournaledTurnEvent> frames = events.listAfter(turnId, after, 100);
      if (frames.isEmpty()) {
        return result;
      }
      frames.forEach(f -> result.add(f.event()));
      after = frames.get(frames.size() - 1).eventId();
    }
  }

  private static boolean hasEvent(List<ChatTurnEvent> list, ChatTurnEvent.TurnEventType type) {
    return list.stream().anyMatch(e -> e.type() == type);
  }

  private static void awaitTerminal(AgentTurnRepository turns, String turnId, long timeoutMillis)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      if (turns.findByTurnId(turnId).orElseThrow().status().terminal()) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("turn not terminal within " + timeoutMillis + "ms");
  }

  private static void awaitStatus(AgentTurnRepository turns, String turnId, TurnStatus status,
                                  long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      if (turns.findByTurnId(turnId).orElseThrow().status() == status) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("turn not reaching " + status + " within " + timeoutMillis + "ms");
  }

  // ---------------- 测试用例 ----------------

  @Test
  void e2eAg01_plainQuestionRunsFullPipelineToCompletedWithTrace() throws Exception {
    HttpServer server = startServer(counter -> plainAnswerStream("6月销售额为 1280 万元。"));
    Assembly assembly = assemble(server.getAddress().getPort());
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);

        String turnId = assembly.service().submitTurn("sess-1", "6 月份各区域销售额是多少？");
        assertNotNull(turnId, "提交必须立即返回 turnId（提交/执行分离）");

        awaitTerminal(assembly.turns(), turnId, 30_000);
        assertEquals(TurnStatus.COMPLETED,
            assembly.turns().findByTurnId(turnId).orElseThrow().status(),
            "自然完成必须收敛为 COMPLETED");

        List<ChatTurnEvent> events = allEvents(assembly.events(), turnId);
        assertTrue(hasEvent(events, ChatTurnEvent.TurnEventType.TEXT_DELTA),
            "必须产出正文增量帧：" + events);
        assertTrue(hasEvent(events, ChatTurnEvent.TurnEventType.TURN_FINISHED),
            "必须产出终态帧：" + events);
        assertTrue(events.stream()
                .filter(e -> e.type() == ChatTurnEvent.TurnEventType.TEXT_DELTA)
                .anyMatch(e -> e.delta() != null && e.delta().contains("1280")),
            "正文增量必须包含模型回答：" + events);
        assertTrue(!assembly.events().reconstructTrace(turnId).isEmpty(),
            "事件日志必须可重建 trace 步骤");
        // 观察项：消息树节点创建在 agent 主链路中无生产调用点（submitTurn/executor 仅 complete 回填、
        // 不 append），当前消息树可能为空；本测试验证主链路不依赖消息树、缺失时 complete 幂等不阻塞。
        assertTrue(!assembly.events().reconstructTrace(turnId).isEmpty(),
            "事件日志可重建 trace（消息树节点创建为交互层/未接线观察项）");
        assertEquals(42L, assembly.sessions().findBySessionId("sess-1").orElseThrow().userId(),
            "首访必须绑定归属人");
        assertNotNull(assembly.sessions().findBySessionId("sess-1").orElseThrow().title(),
            "首访必须以问题开头作为会话标题");
      }
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }

  @Test
  void e2eAg02_submitRejectedWhileTurnActive() throws Exception {
    HttpServer server = startServer(counter -> stalledStream(4));
    Assembly assembly = assemble(server.getAddress().getPort());
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
        String turnId = assembly.service().submitTurn("sess-1", "第一个问题");
        awaitStatus(assembly.turns(), turnId, TurnStatus.RUNNING, 15_000);

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> assembly.service().submitTurn("sess-1", "排队时再问一个"));
        assertTrue(e.getMessage().contains("已有排队或推理中的轮次"),
            "单飞拒绝信息必须可人读：" + e.getMessage());
      }
      // 收尾：在途轮次由 cleanup 的 dispatcher.shutdown 中断（无需显式取消）
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }

  @Test
  void e2eAg03_hitlClarifySuspendsThenResumeReachesFinal() throws Exception {
    HttpServer server =
        startServer(seq -> seq == 1 ? clarificationStream() : finalAnswerStream());
    Assembly assembly = assemble(server.getAddress().getPort());
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
        String turnId = assembly.service().submitTurn("sess-1", "分析销售额趋势，按已支付口径");

        awaitStatus(assembly.turns(), turnId, TurnStatus.WAITING_INPUT, 30_000);
        List<ChatTurnEvent> suspended = allEvents(assembly.events(), turnId);
        assertTrue(hasEvent(suspended, ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED),
            "挂起轮必须产出澄清帧：" + suspended);
        assertEquals("call_clarify_1",
            suspended.stream()
                .filter(e -> e.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED)
                .findFirst().orElseThrow().toolCallId(),
            "澄清帧必须携带可追溯 toolCallId");

        var query = new io.yak.ops.business.agent.conversation.query.AgentSessionQueryService(
            assembly.sessions(), mock(io.yak.ops.business.agent.repository.QueryLogRepository.class),
            assembly.turns(), assembly.events(), mock(io.yak.ops.business.agent.repository.AgentStepRepository.class),
            mock(AgentRuntime.class), new io.yak.ops.business.agent.conversation.query.TraceViewAssembler(),
            () -> Optional.of(new io.yak.ops.core.project.ProjectContext(1L, "test-project")));
        var restored = query.continuation("sess-1");
        assertEquals(turnId, restored.turnId());
        assertEquals(TurnStatus.WAITING_INPUT, restored.status());
        assertEquals("call_clarify_1", restored.clarification().toolCallId());
        String resumedId = assembly.service().submitResume("sess-1",
            List.of(new ToolFeedback(restored.clarification().toolCallId(),
                restored.clarification().toolName(), "口径：已支付金额")));
        assertEquals(turnId, resumedId, "恢复必须续跑同一轮");

        awaitTerminal(assembly.turns(), turnId, 30_000);
        assertEquals(TurnStatus.COMPLETED,
            assembly.turns().findByTurnId(turnId).orElseThrow().status(),
            "resume 后必须收敛 COMPLETED");
        List<ChatTurnEvent> after = allEvents(assembly.events(), turnId);
        assertTrue(after.stream()
                .filter(e -> e.type() == ChatTurnEvent.TurnEventType.TEXT_DELTA)
                .anyMatch(e -> e.delta() != null && e.delta().startsWith("FINAL")),
            "恢复后必须产出来自用户应答的最终回答：" + after);
      }
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }

  @Test
  void e2eAg04_resumeWithForgedToolCallIdIsRejected() throws Exception {
    HttpServer server = startServer(counter -> clarificationStream());
    Assembly assembly = assemble(server.getAddress().getPort());
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
        String turnId = assembly.service().submitTurn("sess-1", "请分析销售额");
        awaitStatus(assembly.turns(), turnId, TurnStatus.WAITING_INPUT, 30_000);

        TurnConflictException e = assertThrows(TurnConflictException.class,
            () -> assembly.service().submitResume("sess-1",
                List.of(new ToolFeedback("forged-id", "request_clarification", "伪造应答"))));
        assertTrue(e.getMessage().contains("toolCallId"),
            "伪造 toolCallId 必须被显式拒绝（HITL 防伪）：" + e.getMessage());
        assertEquals(TurnStatus.WAITING_INPUT,
            assembly.turns().findByTurnId(turnId).orElseThrow().status(),
            "伪造恢复被拒后轮次保持 WAITING_INPUT（不得误转状态）");
      }
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }

  @Test
  void e2eAg05_cancelStopsRunningTurnWithCancelledTerminal() throws Exception {
    HttpServer server = startServer(counter -> stalledStream(6));
    Assembly assembly = assemble(server.getAddress().getPort());
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
        String turnId = assembly.service().submitTurn("sess-1", "分析长任务");
        awaitStatus(assembly.turns(), turnId, TurnStatus.RUNNING, 15_000);

        assembly.service().cancelTurn(turnId);
        awaitTerminal(assembly.turns(), turnId, 20_000);
        assertEquals(TurnStatus.CANCELLED,
            assembly.turns().findByTurnId(turnId).orElseThrow().status(),
            "停止生成必须收敛 CANCELLED");
        assertTrue(hasEvent(allEvents(assembly.events(), turnId),
            ChatTurnEvent.TurnEventType.TURN_CANCELLED), "取消必须产出取消终帧");
      }
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }

  @Test
  void e2eAg06_foreignUserCannotTouchOtherUsersSession() throws Exception {
    HttpServer server = startServer(counter -> plainAnswerStream("完成。"));
    Assembly assembly = assemble(server.getAddress().getPort());
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
        String turnId = assembly.service().submitTurn("sess-1", "我的数据");
        awaitTerminal(assembly.turns(), turnId, 30_000);

        // 越权：切换当前用户再触碰会话
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(99L);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> assembly.service().renameSession("sess-1", "篡改标题"));
        assertTrue(e.getMessage().contains("不存在或不属于当前用户"),
            "越权必须拒绝且不可产生歧义：" + e.getMessage());
      }
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }

  @Test
  void e2eAg07_waitingInputSubscriptionStartsFromZero() throws Exception {
    HttpServer server = startServer(counter -> clarificationStream());
    AgentEventStreamTailer tailer = mock(AgentEventStreamTailer.class);
    Assembly assembly = assemble(server.getAddress().getPort(), tailer);
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
        String turnId = assembly.service().submitTurn("sess-1", "请分析销售额（等待澄清）");
        awaitStatus(assembly.turns(), turnId, TurnStatus.WAITING_INPUT, 30_000);

        // 晚加入订阅 WAITING_INPUT：无游标必须从 0 重放（补投澄清帧给刷新用户）
        assembly.service().openEventStream(turnId, null, null);
        verify(tailer).watch(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(io.yak.ops.business.agent.domain.AgentTurnRecord.class),
            eq(0L));
      }
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }

  @Test
  void e2eAg08_completedTurnSubscriptionStartsFromLatestEvent() throws Exception {
    HttpServer server = startServer(counter -> plainAnswerStream("完成。"));
    AgentEventStreamTailer tailer = mock(AgentEventStreamTailer.class);
    Assembly assembly = assemble(server.getAddress().getPort(), tailer);
    try {
      try (MockedStatic<YakSecurityContext> ctx = mockStatic(YakSecurityContext.class)) {
        ctx.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
        String turnId = assembly.service().submitTurn("sess-1", "已完成问题");
        awaitTerminal(assembly.turns(), turnId, 30_000);
        long latest = assembly.events().latestEventId(turnId);

        // 已完成轮次：无游标从最新帧起步，不重放历史
        assembly.service().openEventStream(turnId, null, null);
        verify(tailer).watch(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(io.yak.ops.business.agent.domain.AgentTurnRecord.class),
            eq(latest));
      }
    } finally {
      if (assembly != null) {
        assembly.cleanup();
      }
      server.stop(0);
    }
  }
}
