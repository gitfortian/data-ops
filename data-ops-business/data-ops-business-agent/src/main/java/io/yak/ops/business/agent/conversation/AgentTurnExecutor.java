package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.MessageTreeRepository;
import io.yak.ops.business.agent.repository.support.TurnInputCodec;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.business.agent.runtime.TurnSubscription;
import io.yak.ops.business.agent.memory.MemoryFlushService;
import io.yak.ops.business.agent.runtime.AgentObservationCollector;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 轮次执行器：认领（CAS QUEUED->RUNNING）后驱动一轮推理，事件先落投递日志再供订阅端拉取；
 * 无论成功/失败/取消都收敛轮次终态并完成消息树占位节点。断连不影响本流程。
 *
 * <p>顺序：claim -> 归属内单飞占用 -> 消息树节点（START）-> 订阅 runtime 事件流
 * -> 逐帧持久化 -> 终态 CAS + 终帧兜底 + 单飞释放。</p>
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class AgentTurnExecutor {

  private final AgentRuntime agentRuntime;
  private final AgentTurnRepository turnRepository;
  private final AgentTurnEventRepository eventRepository;
  private final MessageTreeRepository messageTreeRepository;
  private final AgentTurnRegistry turnRegistry;
  private final AgentObservationCollector observationCollector;
  private final MemoryFlushService memoryFlushService;
  private final ProjectContextScope projectContextScope;

  public void execute(AgentTurnRecord record) {
    startExecution(record, new CountDownLatch(1));
  }

  /** Dispatcher-only admission lease: keep a worker until inference settles or shutdown interrupts. */
  void executeAndAwait(AgentTurnRecord record) {
    CountDownLatch completed = new CountDownLatch(1);
    startExecution(record, completed);
    try {
      completed.await();
    } catch (InterruptedException shutdown) {
      Thread.currentThread().interrupt();
      // Leave durable RUNNING truth for the existing orphan -> INTERRUPTED recovery policy.
      turnRegistry.detach(record.turnId());
    }
  }

  private void startExecution(AgentTurnRecord record, CountDownLatch completed) {
    // PROJECT_RUNTIME：异步上下文恢复——从轮次持久化的 projectId 恢复项目空间，
    // 使工具调用内 CurrentProject.requireProjectId() 可用。
    long projectId = record.projectId();
    if (projectId <= 0) {
      log.error("turn has no project binding, cannot execute: turnId={}", record.turnId());
      turnRepository.fail(record.turnId(), "NO_PROJECT", "会话未绑定项目空间，无法执行推理");
      appendQuietly(record.turnId(), ChatTurnEvent.error("会话未绑定项目空间，无法执行推理"));
      completed.countDown();
      return;
    }
    projectContextScope.run(
        new ProjectContext(projectId, "agent-turn"),
        () -> doExecute(record, completed));
  }

  private void doExecute(AgentTurnRecord record, CountDownLatch completed) {
    String turnId = record.turnId();
    String sessionId = record.sessionId();
    TurnInput input;
    try {
      input = TurnInputCodec.decode(record.payloadJson());
    } catch (RuntimeException e) {
      if (!turnRepository.claimForExecution(turnId)) {
        completed.countDown();
        return;
      }
      turnRepository.fail(turnId, "GENERIC", "轮次输入投影损坏：" + e.getMessage());
      appendQuietly(turnId, ChatTurnEvent.error("轮次输入无效，已终止执行"));
      completed.countDown();
      return;
    }
    TurnState state = new TurnState(completed);
    state.startMillis = System.currentTimeMillis();
    DeferredSubscription deferred = new DeferredSubscription();
    if (!turnRegistry.claimAndRegister(sessionId, turnId, deferred,
        () -> finishCancelled(record, input, state),
        () -> turnRepository.claimForExecution(turnId))) {
      completed.countDown();
      return;
    }
    if (state.completed.getCount() == 0) return;
    if (record.kind() == io.yak.ops.business.agent.domain.TurnKind.RESUME
        && input.feedbacks() != null) {
      // O3 HITL 观测：恢复事实落 span（载荷=用户应答原文，口径沉淀的一等来源）
      for (io.yak.ops.business.agent.domain.ToolFeedback feedback : input.feedbacks()) {
        observationCollector.event(record.sessionId(), record.turnId(),
            AgentStepRecorder.KIND_HITL,
            feedback.toolName() == null ? "resume" : feedback.toolName(),
            feedback.toolCallId(), "COMPLETED", feedback.output(), null, null, null);
      }
    }

    try {
      TurnSubscription subscription =
          record.kind() == io.yak.ops.business.agent.domain.TurnKind.START
              ? agentRuntime.stream(
                  record.userId(),
                  sessionId,
                  turnId,
                  input.message(),
                  record.projectId(),
                  input.governanceTarget(),
                  consume(record, state),
                  () -> finishCompleted(record, input, state),
                  error -> finishFailed(record, input, state, error))
              : agentRuntime.resume(
                  record.userId(),
                  sessionId,
                  turnId,
                  input.feedbacks(),
                  record.projectId(),
                  input.governanceTarget(),
                  consume(record, state),
                  () -> finishCompleted(record, input, state),
                  error -> finishFailed(record, input, state, error));
      // Late cancellation handles are disposed immediately if construction was cancelled.
      deferred.attach(subscription);
      // A synchronous completion or cancellation may have settled during construction.
      if (state.completed.getCount() == 0) turnRegistry.unregister(turnId);
    } catch (RuntimeException assembleError) {
      finishFailed(record, input, state, assembleError);
    }

  }

  private Consumer<ChatTurnEvent> consume(AgentTurnRecord record, TurnState state) {
    return event -> {
      if (state.completed.getCount() == 0) return;
      // 轮次终帧补服务端权威耗时（I2）：事件帧与 step 的计时都出自执行器，前端不再本地掐表
      if (event.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED) {
        event = event.withElapsedMs(elapsed(state));
      }
      long eventId = eventRepository.append(record.turnId(), event);
      state.lastFrame.set(new io.yak.ops.business.agent.domain.JournaledTurnEvent(eventId, event));
      // 终态收敛律（设计稿 §11.3）：跟踪未闭合工具调用，任一终态前强制闭合
      if (event.type() == ChatTurnEvent.TurnEventType.TOOL_CALL && event.toolCallId() != null) {
        state.pendingTools.put(event.toolCallId(), event);
        if (event.toolName() != null) {
          state.toolNames.add(event.toolName());
        }
      } else if (event.type() == ChatTurnEvent.TurnEventType.TOOL_RESULT
          && event.toolCallId() != null) {
        state.pendingTools.remove(event.toolCallId());
      } else if (event.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED
          && event.toolCallId() != null) {
        state.clarifyToolCallId = event.toolCallId();
        state.clarifyQuestion = event.delta();
        state.clarifyToolName = event.toolName();
      }
      if (event.type() == ChatTurnEvent.TurnEventType.TEXT_DELTA && event.delta() != null) {
        state.answer.append(event.delta());
      }
      if (event.type() == ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED) {
        state.clarified.set(true);
      }
      if (event.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED) {
        if (event.totalTokens() != null && event.totalTokens() > 0) {
          state.totalTokens.set(event.totalTokens());
        }
        observationCollector.completedEvent(record.sessionId(), record.turnId(),
            AgentStepRecorder.KIND_TURN_SUMMARY, "turn",
            null, turnSummaryStats(state));
      }
    };
  }

  private void finishCompleted(AgentTurnRecord record, TurnInput input, TurnState state) {
    try {
      if (state.completed.getCount() == 0) return;
      if (state.clarified.get()) {
        // O3 HITL 观测：挂起事实落 span（toolCallId 与事件帧 join；载荷=澄清问题）
        observationCollector.event(record.sessionId(), record.turnId(),
            AgentStepRecorder.KIND_HITL,
            state.clarifyToolName == null ? "request_clarification" : state.clarifyToolName,
            state.clarifyToolCallId, "COMPLETED", state.clarifyQuestion, null, null, null);
        // HITL 合法挂起：澄清工具保持开启（WAITING_INPUT 语义），其余未闭合工具收敛
        if (state.clarifyToolCallId != null) {
          state.pendingTools.remove(state.clarifyToolCallId);
        }
        convergePendingTools(record, state, "WAITING_INPUT");
        turnRepository.markWaitingInput(record.turnId());
      } else {
        convergePendingTools(record, state, "COMPLETED");
        // 记忆线 M1：自然完成轮异步提取长期记忆（best-effort，不阻塞终态；仅 START 轮）
        if (record.kind() == io.yak.ops.business.agent.domain.TurnKind.START) {
          final String flushTurnId = record.turnId();
          final String flushSessionId = record.sessionId();
          memoryFlushService.submitAfterTurn(record.userId(), record.sessionId(), record.turnId(),
              input.message(), state.answer.toString(), state.toolNames,
              (turnId, outcome, detail, extracted, inserted) ->
                  observationCollector.event(flushSessionId, flushTurnId,
                      io.yak.ops.business.agent.telemetry.AgentStepRecorder.KIND_MEMORY_FLUSH,
                      "extract", input.message(), outcome, null, null,
                      "COMPLETED".equals(outcome) ? null : outcome, detail));
        }
        if (!frameAppended(state, ChatTurnEvent.TurnEventType.TURN_FINISHED)) {
          appendQuietly(record.turnId(),
              ChatTurnEvent.finished(orZero(state.totalTokens), elapsed(state)));
        }
        turnRepository.complete(record.turnId());
      }
      completeAssistant(record, input, state);
    } finally {
      settle(record.turnId(), state);
    }
  }

  private void finishFailed(
      AgentTurnRecord record, TurnInput input, TurnState state, Throwable error) {
    try {
      if (state.completed.getCount() == 0) return;
      String code = classify(error);
      String preview = safeMessage(error);
      // 终态收敛律：失败前先闭合全部未结算工具调用，前端不得残留运行中卡片
      convergePendingTools(record, state, "FAILED");
      boolean flipped = turnRepository.fail(record.turnId(), code, preview);
      if (!flipped) {
        log.debug("fail CAS lost (already terminal): turnId={}", record.turnId());
      }
      // I6：错误帧携带分类错误码，前端可分类展示而非只读文本
      appendQuietly(record.turnId(), ChatTurnEvent.error(preview, code));
      completeAssistant(record, input, state);
    } finally {
      settle(record.turnId(), state);
    }
  }

  /** 停止生成终态：registry 在 dispose 上游之前确认取消事实。 */
  private void finishCancelled(
      AgentTurnRecord record, TurnInput input, TurnState state) {
    try {
      // 终态收敛律：取消前先闭合全部未结算工具调用（治"停止生成后卡片永久转圈"）
      convergePendingTools(record, state, "CANCELLED");
      if (!turnRepository.cancelRunning(record.turnId())) {
        log.debug("cancel CAS lost on terminal turn: turnId={}", record.turnId());
      }
      appendQuietly(record.turnId(), ChatTurnEvent.of(ChatTurnEvent.TurnEventType.TURN_CANCELLED));
      completeAssistant(record, input, state);
    } finally {
      settle(record.turnId(), state);
    }
  }

  /**
   * 终态收敛律（设计稿 §11.3 规则 3）：为全部未闭合 toolCallId 补发 ABORTED 结果帧。
   * 正常完成本应为空集，非空即服务端违例（告警留痕，不静默脑补为完整）。
   */
  private void convergePendingTools(AgentTurnRecord record, TurnState state, String terminal) {
    if (state.pendingTools.isEmpty()) {
      return;
    }
    log.warn("terminal state with pending tool calls ({}): turnId={}, toolCallIds={}",
        terminal, record.turnId(), state.pendingTools.keySet());
    for (ChatTurnEvent call : state.pendingTools.values()) {
      appendQuietly(record.turnId(), ChatTurnEvent
          .tool(ChatTurnEvent.TurnEventType.TOOL_RESULT, call.toolCallId(), call.toolName(), null)
          .withIter(call.iter() == null ? 0 : call.iter())
          .withToolOutcome(null, -1L, "ABORTED"));
    }
    state.pendingTools.clear();
  }

  private void settle(String turnId, TurnState state) {
    try {
      turnRegistry.unregister(turnId);
      // 轮次终态：清理 turn 级记账状态（工具父链映射），防长生命周期泄漏
      observationCollector.clearTurn(turnId);
    } finally {
      state.completed.countDown();
    }
  }

  private static String turnSummaryStats(TurnState state) {
    return "{\"totalTokens\":" + orZero(state.totalTokens)
        + ",\"latencyMs\":" + elapsed(state) + "}";
  }

  /** 服务端权威轮次耗时（claim 起至当前，毫秒）。 */
  private static long elapsed(TurnState state) {
    return System.currentTimeMillis() - state.startMillis;
  }

  /** 完成消息树占位节点：已流式输出多少写多少，取消/失败时保留部分正文而非伪造完整答案。 */
  private void completeAssistant(AgentTurnRecord record, TurnInput input, TurnState state) {
    try {
      String content = state.answer.toString();
      Long tokens = state.totalTokens.get() > 0 ? state.totalTokens.get() : null;
      messageTreeRepository.complete(record.sessionId(), input.assistantMessageId(), content, tokens);
    } catch (RuntimeException e) {
      log.warn("assistant node finalize failed: turnId={}", record.turnId(), e);
    }
  }

  private long appendQuietly(String turnId, ChatTurnEvent event) {
    try {
      return eventRepository.append(turnId, event);
    } catch (RuntimeException e) {
      log.warn("event append failed: turnId={}, type={}", turnId, event.type(), e);
      return -1L;
    }
  }

  private static boolean frameAppended(TurnState state, ChatTurnEvent.TurnEventType type) {
    var journaled = state.lastFrame.get();
    return journaled != null
        && journaled.event().type() == type
        && journaled.event().totalTokens() != null;
  }

  private static String classify(Throwable error) {
    String marker = error.getMessage() == null ? "" : error.getMessage();
    if (marker.contains("[TURN_TIMEOUT]") || marker.contains("[LLM_CALL_TIMEOUT]")) {
      return "TIMEOUT";
    }
    if (marker.contains("[LLM_USER_ERROR]") || marker.contains("不存在或不属于当前用户")) {
      return "USER_ERROR";
    }
    if (marker.contains("[GUARD_REJECTED]")) {
      return "GUARD_REJECTED";
    }
    if (marker.contains("[LLM_PROVIDER_ERROR]")) {
      return "PROVIDER_ERROR";
    }
    return "GENERIC";
  }

  private static long orZero(AtomicLong value) {
    long v = value.get();
    return v > 0 ? v : 0L;
  }

  private static String safeMessage(Throwable error) {
    String message = error.getMessage();
    return message == null || message.isBlank()
        ? "推理执行失败：" + error.getClass().getSimpleName()
        : message;
  }


  /** Cancellation can arrive while runtime.stream is still constructing its handle. */
  private static final class DeferredSubscription implements TurnSubscription {
    private TurnSubscription subscription;
    private boolean disposed;

    synchronized void attach(TurnSubscription subscription) {
      if (disposed) subscription.dispose();
      else this.subscription = subscription;
    }

    @Override
    public synchronized void dispose() {
      disposed = true;
      if (subscription != null) {
        subscription.dispose();
        subscription = null;
      }
    }
  }

  /** 一轮执行的运行时可变状态（事件驱动串行访问，无跨线程共享）。 */
  private static class TurnState {
    final CountDownLatch completed;
    TurnState(CountDownLatch completed) {
      this.completed = completed;
    }
    /** claim 成功时刻（服务端权威轮次耗时锚点，I2）。 */
    long startMillis;
    final StringBuilder answer = new StringBuilder();
    final AtomicLong totalTokens = new AtomicLong();
    final AtomicBoolean clarified = new AtomicBoolean(false);
    final AtomicReference<io.yak.ops.business.agent.domain.JournaledTurnEvent> lastFrame =
        new AtomicReference<>();
    /** 终态收敛律：未闭合工具调用（toolCallId -> 原 TOOL_CALL 帧，含 name/iter）。 */
    final java.util.Map<String, ChatTurnEvent> pendingTools = new java.util.LinkedHashMap<>();
    /** 本轮工具名序列（记忆提取输入）。 */
    final java.util.List<String> toolNames = new java.util.ArrayList<>();
    /** HITL 澄清工具调用（WAITING_INPUT 合法保持开启，不参与收敛）。 */
    String clarifyToolCallId;
    /** HITL 澄清问题与工具名（O3：HITL 挂起 span 的载荷）。 */
    String clarifyQuestion;
    String clarifyToolName;
  }
}
