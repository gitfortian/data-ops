package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.HistoryTurn;
import io.yak.ops.business.agent.domain.ToolFeedback;
import io.yak.ops.business.agent.toolset.AgentToolBox;
import io.yak.ops.business.agent.toolset.AgentSystemPromptContributor;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 无状态 ReActAgent 单例运行时：一次组装，所有会话并发复用；
 * 每次调用通过 RuntimeContext(userId, sessionId) 传播身份，状态由官方 StateStore 持久化。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class AgentRuntime implements TurnCorrelation {

  private final List<AgentToolBox> toolBoxes;
  private final List<AgentSystemPromptContributor> promptContributors;
  private final AgentStateStore stateStore;
  private final AgentProperties properties;
  private final AgentEventCodec eventCodec;
  private final io.yak.ops.business.agent.telemetry.AgentStepRecorder stepRecorder;
  private final AgentObservationCollector observationCollector;
  private final io.yak.ops.business.agent.repository.AgentDynamicConfigService dynamicConfig;
  private final io.yak.ops.business.agent.memory.MemoryRecallService memoryRecallService;
  private final io.yak.ops.business.agent.memory.MemoryRepository memoryRepository;

  /** 项目空间上下文恢复（ProjectContextMiddleware 依赖注入）。 */
  private final ProjectContextScope projectContextScope;

  /**
   * 技能在线管理持久化仓库（skills 在线管理热生效路径；可选）。
   * 非 final + 方法注入：不破坏现有 10 参构造（既有测试装配不变）；
   * Spring 装配时经 {@code setSkillRepository} 注入 adapter，未注入则技能面不激活（与现状一致）。
   */
  private volatile io.agentscope.core.skill.repository.AgentSkillRepository skillRepository;

  /**
   * 技能中间件（懒组装后建立）。SkillBox 由框架在首次推理 reloadSkills 时懒建、签名变化时重建，
   * 因此不缓存 box 引用——getSkillBox() 每次从中间件现取（热启停始终命中当前权威 box）。
   */
  private volatile io.agentscope.core.skill.DynamicSkillMiddleware skillMiddleware;

  /** 懒组装：模块启用但模型配置缺失时，应用照常启动，首次对话才报出可操作的错误。 */
  private volatile ReActAgent agent;

  private final io.yak.ops.business.agent.toolset.GovernanceEvidenceTools governanceTools;

  /** Compatibility for existing runtime fixtures; Spring uses the complete constructor above. */
  public AgentRuntime(List<AgentToolBox> toolBoxes,
      List<AgentSystemPromptContributor> promptContributors, AgentStateStore stateStore,
      AgentProperties properties, AgentEventCodec eventCodec,
      io.yak.ops.business.agent.telemetry.AgentStepRecorder stepRecorder,
      AgentObservationCollector observationCollector,
      io.yak.ops.business.agent.repository.AgentDynamicConfigService dynamicConfig,
      io.yak.ops.business.agent.memory.MemoryRecallService memoryRecallService,
      io.yak.ops.business.agent.memory.MemoryRepository memoryRepository,
      ProjectContextScope projectContextScope) {
    this(toolBoxes, promptContributors, stateStore, properties, eventCodec, stepRecorder,
        observationCollector, dynamicConfig, memoryRecallService, memoryRepository,
        projectContextScope, null);
  }

  /** 会话 -> 活跃轮次（调用级记账归因）；推理单飞保证同会话至多一个映射，终态即清除。 */
  private final java.util.Map<String, String> activeTurnBySession =
      new java.util.concurrent.ConcurrentHashMap<>();

  @Override
  public String turnIdOf(String sessionId) {
    return activeTurnBySession.get(sessionId);
  }

  /** 运行时技能盒（热启停目标；未装配技能面或未发生首次推理时为 null —— 管理服务按冷路径落库）。 */
  public io.agentscope.core.skill.SkillBox getSkillBox() {
    io.agentscope.core.skill.DynamicSkillMiddleware middleware = skillMiddleware;
    return middleware == null ? null : middleware.getCurrentSkillBox();
  }

  /**
   * 技能仓库注入（Spring 可选装配；不破坏现有构造）。装配后的技能面：
   * 每次推理 DynamicSkillMiddleware 现读仓库（签名变化重建 SkillBox、注入技能提示），
   * 在线注册/启停对下一次推理热生效。
   */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setSkillRepository(io.agentscope.core.skill.repository.AgentSkillRepository skillRepository) {
    this.skillRepository = skillRepository;
  }

  private ReActAgent agent() {
    ReActAgent local = agent;
    if (local == null) {
      synchronized (this) {
        local = agent;
        if (local == null) {
          local = doAssemble();
          agent = local;
        }
      }
    }
    return local;
  }

  private ReActAgent doAssemble() {
    String provider = properties.getModel().getProvider();
    if (!"openai".equalsIgnoreCase(provider)) {
      throw new IllegalStateException(
          "不支持的模型 provider '"
              + provider
              + "'，当前仅支持 openai。请修正 yak.agent.model.provider，"
              + "或设置 yak.agent.enabled=false 关闭模块");
    }
    if (isBlank(properties.getModel().getBaseUrl())
        || isBlank(properties.getModel().getApiKey())
        || isBlank(properties.getModel().getName())) {
      throw new IllegalStateException(
          "yak.agent.model 配置不完整（base-url / api-key / name 存在空值）。"
              + "请通过环境变量 YAK_AGENT_MODEL_* 注入后重启，"
              + "或设置 yak.agent.enabled=false 关闭模块");
    }

    Toolkit toolkit = new Toolkit();
    toolBoxes.forEach(toolkit::registerTool);
    TaskToolPolicyMiddleware.guardTools(toolkit);

    io.agentscope.core.model.Model model = openAiModel();
    ReActAgent.Builder builder =
        ReActAgent.builder()
            .name("YakOpsAgent")
            .sysPrompt(systemPrompt())
            .model(openAiModel())
            .toolkit(toolkit)
            .stateStore(stateStore)
            // 模型调用 seam：超时/重试分类/每次尝试调用级记账（DOMAIN 运行安全要求）+ O1 内容观测
            .middleware(
                new LlmResilienceMiddleware(
                    properties.getChat().getLlmCallTimeoutSeconds(),
                    properties.getChat().getLlmMaxRetries(),
                    properties.getModel().getName(),
                    observationCollector,
                    this,
                    dynamicConfig))
            // 工具执行审计 seam
            .middleware(new ToolAuditMiddleware(observationCollector, this))
            .middleware(new TaskToolPolicyMiddleware(properties.getExecution().getMaxModelInputChars()))
            // 项目空间上下文恢复 seam：在 reactor 线程的工具执行中恢复 CurrentProject ThreadLocal
            .middleware(new ProjectContextMiddleware(projectContextScope))
            // 提示词管道：能力域贡献者按序追加（排序与异步由框架 Middleware 承担）
            .middleware(new SystemPromptAssemblyMiddleware(promptContributors))
            // 长期记忆召回注入（记忆线 M1：onSystemPrompt 拦截，开关关闭即直通）
            .middleware(new LongTermMemoryPromptMiddleware(
                memoryRecallService, memoryRepository, stateStore, properties,
                observationCollector, dynamicConfig))
            ;
    if (governanceTools != null) builder.middleware(new GovernanceContextMiddleware(governanceTools,
        toolBoxes.stream().filter(io.yak.ops.business.agent.toolset.GovernanceSuggestionTools.class::isInstance)
            .map(io.yak.ops.business.agent.toolset.GovernanceSuggestionTools.class::cast).findFirst().orElse(null),
        observationCollector, this));
    registerSkillMiddlewareIfEnabled(builder, toolkit);
    registerCompactionIfEnabled(builder, model);
    builder.middleware(new EffectiveConfigMiddleware(properties, toolBoxes, observationCollector, this));
    builder
            // HITL：孤儿 pending（用户放弃应答）自动补合成结果收敛，避免会话永久卡死
            .enablePendingToolRecovery(true)
            .maxIters(properties.getChat().getMaxIters());
    String reasoningEffort = properties.getModel().getReasoningEffort();
    if (!isBlank(reasoningEffort)) {
        // 思维链开关：经网关的 DeepSeek-V4 需显式 reasoning_effort 才输出推理内容
        builder.generateOptions(
            io.agentscope.core.model.GenerateOptions.builder()
                .reasoningEffort(reasoningEffort.trim())
                .build());
    }
    ReActAgent assembled = builder.build();
    log.info(
        "agent runtime assembled: tools={}, maxIters={}, model={}, promptContributors={}",
        toolBoxes.size(),
        properties.getChat().getMaxIters(),
        properties.getModel().getName(),
        promptContributors.size());
    return assembled;
  }

  /** 执行一轮推理，事件经回调推送。返回的句柄用于停止生成取消；取消不影响 StateStore 已落盘事实。 */
  public TurnSubscription stream(
      long userId,
      String sessionId,
      String turnId,
      String message,
      long projectId,
      Consumer<ChatTurnEvent> onEvent,
      Runnable onComplete,
      Consumer<Throwable> onError) {
    return streamEvents(
        List.of(startMessage(message, turnId)), userId, sessionId, turnId, projectId, null, onEvent, onComplete, onError);
  }

  /** HITL 恢复：以匹配 pending 的工具结果续跑被挂起的同一轮推理。 */
  public TurnSubscription resume(
      long userId,
      String sessionId,
      String turnId,
      List<ToolFeedback> feedbacks,
      long projectId,
      Consumer<ChatTurnEvent> onEvent,
      Runnable onComplete,
      Consumer<Throwable> onError) {
    return streamEvents(
        feedbacks.stream()
            .map(feedback -> (Msg) new ToolResultMessage(
                feedback.toolCallId(), feedback.toolName(), feedback.output()))
            .toList(),
        userId,
        sessionId,
        turnId,
        projectId,
        null,
        onEvent,
        onComplete,
        onError);
  }

  public TurnSubscription stream(long userId, String sessionId, String turnId, String message,
      long projectId, io.yak.ops.business.agent.domain.GovernanceTarget target,
      Consumer<ChatTurnEvent> onEvent, Runnable onComplete, Consumer<Throwable> onError) {
    return streamEvents(List.of(startMessage(message, turnId)), userId, sessionId, turnId, projectId,
        target, onEvent, onComplete, onError);
  }

  public TurnSubscription resume(long userId, String sessionId, String turnId, List<ToolFeedback> feedbacks,
      long projectId, io.yak.ops.business.agent.domain.GovernanceTarget target,
      Consumer<ChatTurnEvent> onEvent, Runnable onComplete, Consumer<Throwable> onError) {
    return streamEvents(feedbacks.stream().map(feedback -> (Msg) new ToolResultMessage(
        feedback.toolCallId(), feedback.toolName(), feedback.output())).toList(), userId, sessionId,
        turnId, projectId, target, onEvent, onComplete, onError);
  }
  /**
   * 事件流映射管道。流式增量已承载正文时，结果事件的整段全文不再重复下发
   * （否则前端会出现"打字机播完后又整体重放一遍"）；无增量的降级场景仍由结果事件兜底。
   */
  static Flux<ChatTurnEvent> mapStream(
      reactor.core.publisher.Flux<io.agentscope.core.event.AgentEvent> upstream,
      AgentEventCodec codec) {
    java.util.concurrent.atomic.AtomicBoolean textStreamed =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    // 工具输出文本按 toolCallId 累积（框架以 delta 流式发出），End 时注入 TOOL_RESULT 帧
    java.util.Map<String, StringBuilder> toolText =
        new java.util.concurrent.ConcurrentHashMap<>();
    // 工具调用发起时刻（nanoTime）：ToolCallStart 时记录，ToolResultEnd 时折算单次耗时
    java.util.Map<String, Long> toolStartNanos = new java.util.concurrent.ConcurrentHashMap<>();
    // 思考块计时：块起点 = 上一非思考帧后的第一个 THINKING_DELTA（服务端权威，前端不再掐表）
    long[] thinkingBlockStartNanos = {0L};
    boolean[] insideThinking = {false};
    // Token 用量累计（ModelCallEndEvent.chatUsage）
    java.util.concurrent.atomic.AtomicLong totalTokens = new java.util.concurrent.atomic.AtomicLong();
    // 渲染顺序规范（设计稿 §11.3）：迭代序=成功模型调用边界（事件流侧推导；重试发生在
    // LlmResilienceMiddleware 内部、对事件流不可见，故天然不递增）
    java.util.concurrent.atomic.AtomicInteger iterCounter = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicBoolean resultSinceToolCall = new java.util.concurrent.atomic.AtomicBoolean(false);
    return upstream
        .<ChatTurnEvent>handle(
            (event, sink) -> {
              if (event instanceof io.agentscope.core.event.ToolResultTextDeltaEvent delta) {
                toolText
                    .computeIfAbsent(delta.getToolCallId(), k -> new StringBuilder())
                    .append(delta.getDelta() == null ? "" : delta.getDelta());
                return;
              }
              if (event instanceof io.agentscope.core.event.ModelCallEndEvent callEnd
                  && callEnd.getUsage() != null) {
                totalTokens.addAndGet(
                    callEnd.getUsage().getInputTokens()
                        + callEnd.getUsage().getOutputTokens());
              }
              if (event instanceof io.agentscope.core.event.ToolCallStartEvent started) {
                toolStartNanos.put(started.getToolCallId(), System.nanoTime());
              }
              if (!(event instanceof io.agentscope.core.event.ThinkingBlockDeltaEvent
                  || event instanceof io.agentscope.core.event.ToolResultTextDeltaEvent
                  || event instanceof io.agentscope.core.event.ToolCallStartEvent
                  || event instanceof io.agentscope.core.event.ToolResultEndEvent
                  || event instanceof io.agentscope.core.event.ModelCallEndEvent)) {
                // 非思考/工具增量期间：上一思考块已结束，下一块重新计时
                insideThinking[0] = false;
              }
              ChatTurnEvent mapped = codec.map(event);
              // 增量播完后，结果事件的整段全文不再重复下发；但 CLARIFY_REQUESTED 等非 TEXT_DELTA 必须保留
              if (mapped != null
                  && textStreamed.get()
                  && event instanceof io.agentscope.core.event.AgentResultEvent
                  && mapped.type() == ChatTurnEvent.TurnEventType.TEXT_DELTA) {
                mapped = null;
              }
              if (mapped == null) {
                return;
              }
              // 迭代序与文本分层（§11.3 规则 4/5）：TOOL/THINKING 前沿进新迭代；
              // TOOL_RESULT 开启"结果已见"标记，其后首个 TOOL/THINKING 即下一迭代
              if (mapped.type() == ChatTurnEvent.TurnEventType.TOOL_CALL
                  || mapped.type() == ChatTurnEvent.TurnEventType.THINKING_DELTA) {
                if (iterCounter.get() == 0 || resultSinceToolCall.getAndSet(false)) {
                  iterCounter.incrementAndGet();
                }
                mapped = mapped.withIter(iterCounter.get());
              } else if (mapped.type() == ChatTurnEvent.TurnEventType.TOOL_RESULT) {
                resultSinceToolCall.set(true);
                mapped = mapped.withIter(iterCounter.get());
              } else if (mapped.type() == ChatTurnEvent.TurnEventType.TEXT_DELTA) {
                if (iterCounter.get() == 0) {
                  iterCounter.incrementAndGet();
                }
                mapped = mapped.withIter(iterCounter.get())
                    .withPhase(event instanceof io.agentscope.core.event.AgentResultEvent
                        ? "FINAL" : null);
              }
              if (mapped.type() == ChatTurnEvent.TurnEventType.TOOL_CALL) {
                // 工具发起时刻：前端不再用 Date.now() 掐表
                mapped = mapped.withStartedAt(System.currentTimeMillis());
              }
              if (mapped.type() == ChatTurnEvent.TurnEventType.TOOL_RESULT) {
                StringBuilder buffered = toolText.remove(mapped.toolCallId());
                String result = buffered != null && !buffered.isEmpty() ? buffered.toString() : null;
                // 单次工具耗时（发起时刻折算）与终态（框架 ToolResultEndEvent.state 权威）
                Long startNanos = toolStartNanos.remove(mapped.toolCallId());
                // 计时归一（§八）：与 ToolAuditMiddleware（事实侧）共用同一耗时计算器
                long durationMs =
                    startNanos != null ? MiddlewareErrorSupport.elapsed(startNanos) : -1L;
                String toolStatus =
                    event instanceof io.agentscope.core.event.ToolResultEndEvent end
                            && end.getState() != null
                        ? end.getState().name()
                        : "SUCCESS";
                mapped = mapped.withToolOutcome(result, durationMs, toolStatus);
              }
              if (mapped.type() == ChatTurnEvent.TurnEventType.THINKING_DELTA) {
                // 思考块计时：块起点 = 进入思考后的第一个增量（服务端权威）
                if (!insideThinking[0]) {
                  insideThinking[0] = true;
                  thinkingBlockStartNanos[0] = System.nanoTime();
                }
                mapped =
                    mapped.withThinkingElapsedMs(
                        MiddlewareErrorSupport.elapsed(thinkingBlockStartNanos[0]));
              }
              if (mapped.type() == ChatTurnEvent.TurnEventType.TEXT_DELTA) {
                textStreamed.set(true);
              }
              if (mapped.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED
                  && mapped.totalTokens() == null) {
                long tokens = totalTokens.get();
                mapped =
                    tokens > 0
                        ? ChatTurnEvent.finished(tokens)
                        : mapped;
              }
              sink.next(mapped);
            });
  }

  private TurnSubscription streamEvents(
      List<Msg> inputs,
      long userId,
      String sessionId,
      String turnId,
      long projectId,
      io.yak.ops.business.agent.domain.GovernanceTarget target,
      Consumer<ChatTurnEvent> onEvent,
      Runnable onComplete,
      Consumer<Throwable> onError) {
    RuntimeContext context =
        RuntimeContext.builder()
            .userId(String.valueOf(userId))
            .sessionId(sessionId)
            .build();
    var execution = new io.yak.ops.business.agent.domain.AgentExecutionContext(target);
    context.put(io.yak.ops.business.agent.domain.AgentExecutionContext.class, execution);
    TurnToolBudgetState.attach(stateStore, context, turnId,
        inputs.stream().allMatch(ToolResultMessage.class::isInstance), properties.getExecution());
    // PROJECT_RUNTIME：将 projectId 写入 RuntimeContext 的 stringAttributes，
    // 供 ProjectContextMiddleware 在工具执行线程恢复 ThreadLocal
    if (projectId > 0) {
      context.put(ProjectContextMiddleware.ATTR_PROJECT_ID, projectId);
    }
    if (turnId != null) {
      activeTurnBySession.put(sessionId, turnId);
    }
    Flux<ChatTurnEvent> pipeline =
        withTurnTimeout(
            mapStream(GovernanceAnswerGuard.guard(agent().streamEvents(inputs, context), execution, context, agent()), eventCodec),
            Duration.ofSeconds(properties.getChat().getTurnTimeoutSeconds()))
            .doFinally(signal -> execution.stopTools());
    reactor.core.Disposable disposable = pipeline.subscribe(onEvent::accept, onError, onComplete);
    return () -> {
      execution.stopTools();
      if (turnId != null && turnId.equals(activeTurnBySession.get(sessionId))) {
        activeTurnBySession.remove(sessionId, turnId);
      }
      if (!disposable.isDisposed()) {
        disposable.dispose();
      }
    };
  }

  /**
   * 轮次硬超时闸门（LLM 调用工程第一原则）：超时映射为带 [TURN_TIMEOUT] 错误码的异常，
   * 由上层转为可操作的错误文案；timeout<=0 视为不启用。
   */
  static <T> Flux<T> withTurnTimeout(Flux<T> upstream, Duration timeout) {
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      return upstream;
    }
    return upstream
        .timeout(timeout)
        .onErrorMap(
            java.util.concurrent.TimeoutException.class,
            e -> new IllegalStateException(
                "[TURN_TIMEOUT] 本轮推理超过 "
                    + timeout.toSeconds()
                    + "s 未完成；solution=缩小问题范围、拆分步骤后重试",
                e));
  }

  /** 删除会话在 StateStore 中的全部数据（消息历史与 pending 状态）。报告与会话元数据不在本边界。 */
  public void deleteSessionData(long userId, String sessionId) {
    stateStore.delete(String.valueOf(userId), sessionId);
  }

  /** 只读：按轮次分组重建对话投影（键 agent_state，框架所有）。查询失败返回空列表。 */
  public List<HistoryTurn> history(long userId, String sessionId) {
    List<Msg> context =
        stateStore
            .get(String.valueOf(userId), sessionId, "agent_state", AgentState.class)
            .map(AgentState::getContext)
            .orElseGet(List::of);
    return projectHistory(context);
  }

  /**
   * 轮次分组投影：USER 开启新轮；带工具调用的 ASSISTANT（中间推理）与 TOOL/SYSTEM
   * 消息不进入正文，仅保留每轮最终回答文本，避免刷新后内部步骤被拆成多个气泡。
   */
  static List<HistoryTurn> projectHistory(List<Msg> context) {
    List<HistoryTurn> turns = new java.util.ArrayList<>();
    String pendingUser = null;
    String pendingTurnId = null;
    StringBuilder answer = new StringBuilder();
    for (Msg message : context) {
      MsgRole role = message.getRole();
      if (role == MsgRole.USER) {
        flushTurn(turns, pendingUser, pendingTurnId, answer);
        pendingUser = AgentEventCodec.textOf(message);
        Object reference = message.getMetadata() == null ? null : message.getMetadata().get("yak_turn_id");
        pendingTurnId = reference instanceof String id && !id.isBlank() ? id : null;
        answer.setLength(0);
      } else if (role == MsgRole.ASSISTANT) {
        boolean intermediate =
            !message.getContentBlocks(io.agentscope.core.message.ToolUseBlock.class).isEmpty();
        if (intermediate) {
          continue;
        }
        String text = AgentEventCodec.textOf(message);
        if (!text.isBlank()) {
          if (answer.length() > 0) {
            answer.append("\n\n");
          }
          answer.append(text);
        }
      }
    }
    flushTurn(turns, pendingUser, pendingTurnId, answer);
    return turns;
  }

  private static void flushTurn(
      List<HistoryTurn> turns, String pendingUser, String turnId, StringBuilder answer) {
    if (pendingUser == null) {
      return;
    }
    turns.add(new HistoryTurn("user", pendingUser, turnId));
    if (answer.length() > 0) {
      turns.add(new HistoryTurn("assistant", answer.toString(), turnId));
    }
  }

  /** Server-owned reference in official message state; it carries no lifecycle or source facts. */
  static Msg startMessage(String message, String turnId) {
    return UserMessage.builder().textContent(message)
        .metadata(turnId == null || turnId.isBlank() ? java.util.Map.of() : java.util.Map.of("yak_turn_id", turnId))
        .build();
  }

  /**
   * 技能面装配（skills 在线管理热生效路径）：仓库已注入（Spring 注入 adapter）时，
   * 挂 DynamicSkillMiddleware —— 每次推理现读仓库（技能集签名变化重建 SkillBox、
   * 技能 instructions 追加进 System Prompt）。热启停由管理服务写 DB（持久真相）+
   * 现有 SkillBox.setSkillActive（即时辅助）；冷装配（DB status）在重建时还原。
   */
  private void registerSkillMiddlewareIfEnabled(io.agentscope.core.ReActAgent.Builder builder,
                                                io.agentscope.core.tool.Toolkit toolkit) {
    io.agentscope.core.skill.repository.AgentSkillRepository repo = skillRepository;
    if (repo == null) {
      skillMiddleware = null;
      return;
    }
    try {
      io.agentscope.core.skill.DynamicSkillMiddleware middleware =
          new RuntimeSkillMiddleware(repo, toolkit);
      builder.middleware(middleware);
      skillMiddleware = middleware;
      log.info("skill middleware registered: repository={}", repo.getSource());
    } catch (Exception e) {
      log.warn("skill middleware disabled (init failed): {}", e.getMessage());
    }
  }

  /**
   * 长对话压缩注册（防跨轮上下文超窗静默失败）：Workspace 最小化配置在临时/指定目录；
   * 初始化失败仅降级关闭压缩并告警，不阻断 Agent 装配。
   *
   * <p>显式配置优先：OpenAIChatModel 不报告 context window（返回 0），
   * 框架动态适配无法生效，因此必须通过 AgentProperties.Compaction.contextWindowSize 显式声明。
   * triggerTokens 由 contextWindowSize - reserved 计算；keepTokens 由框架按 keepTokensRatio 收敛。
   *
   * <p>O3 观测边界（设计稿预授权降级）：框架 CompactionMiddleware 字段全私有且无压缩触发
   * 回调缝，COMPACTION span 暂无可靠数据来源（KindSpec 已注册占位）；框架版本提供回调缝后
   * 按 MemoryObservation 同模式接入（登记于可观测性开发计划 O3 执行复盘）。
   */
  private void registerCompactionIfEnabled(ReActAgent.Builder builder, io.agentscope.core.model.Model model) {
    if (!properties.getCompaction().isEnabled()) {
      return;
    }
    try {
      String configured = properties.getCompaction().getWorkspaceDir();
      java.nio.file.Path dir = java.nio.file.Paths.get(
          configured == null || configured.isBlank()
              ? System.getProperty("java.io.tmpdir") + java.io.File.separator + "yak-ops-agent-ws"
              : configured);
      java.nio.file.Files.createDirectories(dir);
      io.agentscope.harness.agent.workspace.WorkspaceManager workspaceManager =
          new io.agentscope.harness.agent.workspace.WorkspaceManager(dir);

      int contextWindow = properties.getCompaction().getContextWindowSize();
      int reserved = properties.getCompaction().getReserved();
      // triggerTokens = contextWindow - reserved；若 contextWindow <= 0 则不启用 token 维度触发
      int triggerTokens = contextWindow > 0 ? Math.max(1, contextWindow - reserved) : 0;

      io.agentscope.harness.agent.memory.compaction.CompactionConfig config =
          new io.agentscope.harness.agent.memory.compaction.CompactionConfig.Builder()
              .triggerMessages(properties.getCompaction().getTriggerMessages())
              .triggerTokens(triggerTokens)
              .reserved(reserved)
              .keepMessages(properties.getCompaction().getKeepMessages())
              .build();
      builder.middleware(
          new io.agentscope.harness.agent.middleware.CompactionMiddleware(
              workspaceManager, model, config));
      log.info(
          "compaction middleware registered: workspace={}, contextWindow={}, triggerTokens={}, triggerMessages={}, keepMessages={}",
          dir, contextWindow, triggerTokens,
          properties.getCompaction().getTriggerMessages(),
          properties.getCompaction().getKeepMessages());
    } catch (Exception e) {
      log.warn("compaction disabled (workspace init failed): {}", e.getMessage());
    }
  }

  /** 记忆提取端口（memory 包防腐接口的 runtime 实现；与主推理同模型端点）。 */
  @org.springframework.context.annotation.Bean
  io.yak.ops.business.agent.memory.MemoryCompletionPort memoryCompletionPort() {
    return new MemoryCompletionAdapter(openAiModel());
  }

  private OpenAIChatModel openAiModel() {
    var model = properties.getModel();
    return OpenAIChatModel.builder()
        .baseUrl(model.getBaseUrl())
        .apiKey(model.getApiKey())
        .modelName(model.getName())
        .stream(true)
        .build();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String systemPrompt() {
    String base =
        """
        你是 Yak Ops 平台的数据分析助手，基于平台数据集回答业务数据分析问题。
        工作约定：
        1. 回答取数类问题前，先用 list_datasets 确定目标数据集，再用 get_dataset_fields 获取字段清单；
        2. 取数一律使用 run_dataset_query 结构化参数完成，禁止尝试生成或执行 SQL；
        3. 涉及相对时间时，先用 current_date_info 获取当前日期事实再换算；
        4. 字段引用必须来自字段清单返回的 fieldId；参数被拒绝时按错误提示修正后重试；
        5. 结论必须基于工具返回的真实查询结果，不得编造数据。
        """;
    return base.strip();
  }


}
