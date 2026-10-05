package io.yak.ops.business.agent.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.memory.MemoryRecallService;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 长期记忆召回注入（记忆线 M1，设计稿 §五；onSystemPrompt 拦截点）：
 * 取会话最近一条用户消息作为查询 → USER/GLOBAL 层检索（置信度门槛+类型权重+时间衰减）
 * → topK + 字符预算截断 → 追加到 system prompt；命中回写异步 best-effort；
 * KIND_MEMORY_RECALL 步骤记录 hit 数/字符数（D4 归因）。注入式而非工具化：
 * 数据分析高频短平快场景，省一次工具往返（设计稿 §5.1 裁决）。
 *
 * <p>由 {@code AgentRuntime} 装配（非 Spring bean）。DB 阻塞读取调度到 boundedElastic。</p>
 */
public class LongTermMemoryPromptMiddleware implements MiddlewareBase {

  private final MemoryRecallService recallService;
  private final io.yak.ops.business.agent.memory.MemoryRepository memoryRepository;
  private final AgentStateStore stateStore;
  private final AgentProperties properties;
  private final AgentObservationCollector observationCollector;
  private final AgentDynamicConfigService dynamicConfig;

  public LongTermMemoryPromptMiddleware(
      MemoryRecallService recallService,
      io.yak.ops.business.agent.memory.MemoryRepository memoryRepository,
      AgentStateStore stateStore,
      AgentProperties properties,
      AgentObservationCollector observationCollector,
      AgentDynamicConfigService dynamicConfig) {
    this.recallService = recallService;
    this.memoryRepository = memoryRepository;
    this.stateStore = stateStore;
    this.properties = properties;
    this.observationCollector = observationCollector;
    this.dynamicConfig = dynamicConfig;
  }

  @Override
  public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
    var execution = context.get(io.yak.ops.business.agent.domain.AgentExecutionContext.class);
    if (execution != null && execution.target() != null) return Mono.just(currentPrompt);
    if (!dynamicConfig.enabled(
        AgentDynamicConfigService.KEY_MEMORY_ENABLED, properties.getMemory().isEnabled())) {
      return Mono.just(currentPrompt);
    }
    long startNanos = System.nanoTime();
    return Mono.fromCallable(() -> recall(context, currentPrompt, startNanos))
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorReturn(currentPrompt);
  }

  private String recall(RuntimeContext context, String currentPrompt, long startNanos) {
    String userId = context.getUserId();
    String sessionId = context.getSessionId();
    String queryText = lastUserMessage(userId, sessionId);
    List<Long> hits = new ArrayList<>();
    String section = recallService.recallSection(userId, queryText, properties.getMemory(), hits);
    if (section.isEmpty()) {
      return currentPrompt;
    }
    observationCollector.event(sessionId, null, AgentStepRecorder.KIND_MEMORY_RECALL,
        "recall", null, "COMPLETED", queryText, section, null,
        "hit=" + hits.size() + ",latencyMs=" + MiddlewareErrorSupport.elapsed(startNanos));
    memoryRepository.markHit(hits);
    return currentPrompt + "\n\n" + section;
  }

  /** 会话最近一条用户消息（召回查询构造；StateStore 无会话返回空串 → 按分数取全量 topK）。 */
  private String lastUserMessage(String userId, String sessionId) {
    try {
      List<Msg> context = stateStore
          .get(userId, sessionId, "agent_state", AgentState.class)
          .map(AgentState::getContext)
          .orElseGet(List::of);
      for (int i = context.size() - 1; i >= 0; i--) {
        Msg message = context.get(i);
        if (message.getRole() == MsgRole.USER) {
          return AgentEventCodec.textOf(message);
        }
      }
    } catch (Exception e) {
      // 无状态会话（首轮）或读取失败：空查询降级
    }
    return "";
  }
}
