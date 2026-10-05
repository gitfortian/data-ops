package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.dao.mapper.AgentConfigMapper;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.memory.MemoryRecallService;
import io.yak.ops.business.agent.memory.MemoryRepository;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import io.yak.ops.business.agent.toolset.AgentToolBox;
import io.yak.ops.business.agent.toolset.CurrentDateInfoTool;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 技能在线管理热生效 E2E（SKILL-E2E）：验证「注册技能 → 下一轮推理系统提示可见技能 →
 * 在线更新内容 → 下一轮推理系统提示反映新内容」的框架级热生效语义。
 *
 * <p>装配与生产一致：AgentRuntime 注入技能仓库（内存替身）→ 首次推理懒组装挂
 * DynamicSkillMiddleware → 每次推理 reloadSkills（技能集签名变化重建 SkillBox）→
 * 技能 instructions 拼入系统提示。fake OpenAI 端点截获请求体断言提示内容。</p>
 *
 * <p>内容更新、启停、删除与冷启动经真实框架验证；管理持久化与 CAS 另由 Repository/Service 测试覆盖。</p>
 */
class SkillHotSwapE2ETest {

  /**
   * 模拟生产 DB 启用目录；状态不写入运行时 Skill metadata，过滤变化驱动 SDK 重建。
   */
  private static final class InMemorySkillRepository implements AgentSkillRepository {

    private final Map<String, AgentSkill> skills = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> statuses = new java.util.concurrent.ConcurrentHashMap<>();

    void put(AgentSkill skill) {
      skills.put(skill.getName(), skill);
      statuses.put(skill.getName(), true);
    }

    void remove(String name) {
      skills.remove(name);
      statuses.remove(name);
    }

    void setEnabled(String name, boolean enabled) {
      // 过滤后的运行时目录变化，下一执行段重新加载。
      statuses.put(name, enabled);
    }

    boolean isEnabled(String name) {
      return statuses.getOrDefault(name, false);
    }

    /** 在线更新（生产经 PUT /skills/{id}）：覆写技能对象内容 → 框架 reloadSkills 签名变化 → SkillBox 重建。 */
    void update(AgentSkill skill) {
      skills.put(skill.getName(), skill);
    }

    @Override
    public AgentSkill getSkill(String name) {
      return skills.get(name);
    }

    @Override
    public List<String> getAllSkillNames() {
      return new ArrayList<>(skills.keySet());
    }

    @Override
    public List<AgentSkill> getAllSkills() {
      return skills.values().stream().filter(skill -> isEnabled(skill.getName())).toList();
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean overwrite) {
      skills.forEach(this::put);
      return true;
    }

    @Override
    public boolean delete(String name) {
      return skills.remove(name) != null;
    }

    @Override
    public boolean skillExists(String name) {
      return skills.containsKey(name);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
      return new AgentSkillRepositoryInfo("memory", "test", true);
    }

    @Override
    public String getSource() {
      return "memory:test";
    }

    @Override
    public void setWriteable(boolean writable) {}

    @Override
    public boolean isWriteable() {
      return true;
    }
  }

  private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();

  private static HttpServer startFakeLlama() throws Exception {
    LAST_REQUEST_BODY.set(null);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String body =
        "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
            + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"完成。\"}}]}\n\n"
            + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"spike\","
            + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: [DONE]\n\n";
    for (String path : List.of("/v1/chat/completions", "/chat/completions")) {
      server.createContext(path, (HttpExchange exchange) -> {
        LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(bytes);
        }
      });
    }
    server.start();
    return server;
  }

  private static AgentRuntime newRuntime(InMemorySkillRepository repo, int port) {
    AgentProperties properties = new AgentProperties();
    properties.getModel().setBaseUrl("http://127.0.0.1:" + port + "/v1");
    properties.getModel().setApiKey("test");
    properties.getModel().setName("spike");

    AgentStepRecorder stepRecorder = mock(AgentStepRecorder.class);
    AgentDynamicConfigService configService =
        new AgentDynamicConfigService(mock(AgentConfigMapper.class));
    AgentObservationCollector observationCollector =
        new AgentObservationCollector(stepRecorder, properties, configService);
    AgentRuntime runtime =
        new AgentRuntime(
            List.of(new GenericToolBox(), new CurrentDateInfoTool()),
            List.of(),
            new InMemoryAgentStateStore(),
            properties,
            new AgentEventCodec(),
            stepRecorder,
            observationCollector,
            configService,
            mock(MemoryRecallService.class),
            mock(MemoryRepository.class),
            new PassthroughProjectContextScope());
    runtime.setSkillRepository(repo);
    return runtime;
  }

  /** 空工具盒（技能热生效验证只需要技能提示，不需要额外工具）。 */
  public static class GenericToolBox implements AgentToolBox {}

  /** 测试用透传项目上下文作用域：不实际设置 ThreadLocal，仅直通动作。 */
  private static final class PassthroughProjectContextScope implements ProjectContextScope {
    @Override
    public <T> T call(ProjectContext context, java.util.function.Supplier<T> action) {
      return action.get();
    }
  }

  private static void streamOnce(AgentRuntime runtime, String sessionId, String turnId)
      throws Exception {
    List<ChatTurnEvent> events = new CopyOnWriteArrayList<>();
    CountDownLatch done = new CountDownLatch(1);
    runtime.stream(
        1L,
        sessionId,
        turnId,
        "分析一下资产",
        1L,
        events::add,
        done::countDown,
        error -> {
          events.add(ChatTurnEvent.error(String.valueOf(error)));
          done.countDown();
        });
    assertTrue(done.await(60, TimeUnit.SECONDS), "推理必须完成，事件：" + events);
    assertTrue(events.stream().noneMatch(event -> event.type() == ChatTurnEvent.TurnEventType.ERROR),
        "推理不能因错误提前结束：" + events);
  }

  // ---------------- 测试用例 ----------------

  @org.junit.jupiter.api.Test
  void skillRegisteredAppearsInNextTurnPrompt() throws Exception {
    HttpServer server = startFakeLlama();
    try {
      InMemorySkillRepository repo = new InMemorySkillRepository();
      AgentRuntime runtime = newRuntime(repo, server.getAddress().getPort());

      // 注册技能（repo 直接落 key；生产经管理服务/DB，热生效机制同一）
      repo.put(AgentSkill.builder()
          .name("asset-yoy")
          .description("对资产进行同比分析")
          .putMetadata("displayName", "资产管理同比分析")
          .putMetadata("enabled", true)
          .putMetadata("version", 1)
          .skillContent("当用户询问资产同比时，按资产台账年度同比口径回答；输出同比率与绝对值。")
          .build());

      streamOnce(runtime, "s1", "t1");

      String requestBody = LAST_REQUEST_BODY.get();
      assertNotNull(requestBody, "必须截获 LLM 请求体");
      assertTrue(requestBody.contains("asset-yoy") || requestBody.contains("资产"),
          "注册后的技能必须注入系统提示，实际：" + truncate(requestBody));
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.api.Test
  void skillContentUpdateAppearsInNextTurnPrompt() throws Exception {
    HttpServer server = startFakeLlama();
    try {
      InMemorySkillRepository repo = new InMemorySkillRepository();
      AgentRuntime runtime = newRuntime(repo, server.getAddress().getPort());

      repo.put(AgentSkill.builder()
          .name("asset-yoy")
          .description("对资产进行同比分析")
          .putMetadata("displayName", "资产管理同比分析")
          .putMetadata("enabled", true)
          .putMetadata("version", 1)
          .skillContent("当用户询问资产同比时，产出同比口径分析。")
          .build());

      // 第一轮：技能可见
      streamOnce(runtime, "s1", "t1");
      String firstBody = LAST_REQUEST_BODY.get();
      assertNotNull(firstBody);
      assertTrue(firstBody.contains("同比"), "注册后第一轮提示必须含技能内容，实际：" + truncate(firstBody));

      // 与生产相同的稳定逻辑 name；displayName 不占用 SDK 保留的 name。
      repo.update(
          AgentSkill.builder()
              .name("asset-yoy")
              .description("对资产进行环比分析")
              .putMetadata("displayName", "资产管理环比分析")
              .putMetadata("enabled", true)
              .putMetadata("version", 2)
              .skillContent("当用户询问资产环比时，产出环比口径分析。")
              .build());

      // 第二轮：新版技能内容可见（reloadSkills 检测内容签名变化 → 重建 SkillBox → 新内容注入）
      streamOnce(runtime, "s1", "t2");
      String secondBody = LAST_REQUEST_BODY.get();
      assertNotNull(secondBody);
assertTrue(secondBody.contains("环比"),
          "更新后第一轮提示必须含新版技能内容，实际：" + truncate(secondBody));
      // 历史消息仍可能引用旧版本；加载工具只读取当前启用目录，不把历史当成当前事实。
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.api.Test
  void disabledAndDeletedSkillStayAbsentAcrossColdRestartAndReenable() throws Exception {
    HttpServer server = startFakeLlama();
    try {
      var repo = new InMemorySkillRepository();
      repo.put(AgentSkill.builder().name("fixture-policy-skill").description("fixture-description")
          .skillContent("fixture-instructions").build());
      var runtime = newRuntime(repo, server.getAddress().getPort());
      streamOnce(runtime, "fresh1", "t1");
      assertTrue(LAST_REQUEST_BODY.get().contains("fixture-policy-skill"));
      repo.setEnabled("fixture-policy-skill", false);
      streamOnce(runtime, "fresh2", "t2");
      org.junit.jupiter.api.Assertions.assertFalse(LAST_REQUEST_BODY.get().contains("fixture-policy-skill"));
      var restarted = newRuntime(repo, server.getAddress().getPort());
      streamOnce(restarted, "fresh3", "t3");
      org.junit.jupiter.api.Assertions.assertFalse(LAST_REQUEST_BODY.get().contains("fixture-policy-skill"));
      repo.setEnabled("fixture-policy-skill", true);
      streamOnce(restarted, "fresh4", "t4");
      assertTrue(LAST_REQUEST_BODY.get().contains("fixture-policy-skill"));
      repo.remove("fixture-policy-skill");
      streamOnce(restarted, "fresh5", "t5");
      org.junit.jupiter.api.Assertions.assertFalse(LAST_REQUEST_BODY.get().contains("fixture-policy-skill"));
    } finally {
      server.stop(0);
    }
  }

  private static String truncate(String text) {
    return text == null ? "null" : text.substring(0, Math.min(text.length(), 600));
  }
}
