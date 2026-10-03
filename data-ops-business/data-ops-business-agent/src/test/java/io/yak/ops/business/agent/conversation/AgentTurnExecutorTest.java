package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.MessageTreeRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import io.yak.ops.business.agent.repository.support.TurnInputCodec;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 轮次执行器可证伪验收：认领后驱动推理，事件先落投递日志；
 * 三类终态（完成/HITL 挂起/失败）与停止生成都必须收敛轮次状态并落笔消息树。
 */
class AgentTurnExecutorTest {

  private AgentRuntime agentRuntime;
  private AgentTurnRepository turnRepository;
  private AgentTurnEventRepository eventRepository;
  private MessageTreeRepository messageTreeRepository;
  private io.yak.ops.business.agent.runtime.AgentObservationCollector observationCollector;

  private Consumer<ChatTurnEvent> onEvent;
  private Runnable onComplete;
  private Consumer<Throwable> onError;
  private AgentTurnRegistry registry;
  private CountDownLatch subscribed;
  private CountDownLatch disposed;

  @BeforeEach
  void setUp() {
    subscribed = new CountDownLatch(1);
    disposed = new CountDownLatch(1);
    agentRuntime = mock(AgentRuntime.class);
    observationCollector = mock(io.yak.ops.business.agent.runtime.AgentObservationCollector.class);
    turnRepository = mock(AgentTurnRepository.class);
    eventRepository = mock(AgentTurnEventRepository.class);
    messageTreeRepository = mock(MessageTreeRepository.class);
    when(turnRepository.claimForExecution(anyString())).thenReturn(true);
    AtomicLong seq = new AtomicLong();
    when(eventRepository.append(anyString(), any()))
        .thenAnswer(inv -> seq.incrementAndGet());
    // runtime.stream 捕获回调，供用例同步驱动
    when(agentRuntime.stream(anyLong(), anyString(), anyString(), anyString(), anyLong(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              onEvent = inv.getArgument(5);
              onComplete = inv.getArgument(6);
              onError = inv.getArgument(7);
              io.yak.ops.business.agent.runtime.TurnSubscription handle = () -> disposed.countDown();
              subscribed.countDown();
              return handle;
            });
    registry = new AgentTurnRegistry();

    var executor =
        new AgentTurnExecutor(
            agentRuntime,
            turnRepository,
            eventRepository,
            messageTreeRepository,
            registry,
            observationCollector,
            mock(io.yak.ops.business.agent.memory.MemoryFlushService.class),
            new PassthroughProjectContextScope());
  }

  private AgentTurnRecord startRecord() {
    String payload = TurnInputCodec.encode(TurnInput.ofStart("u1", "a1", "请分析销售额"));
    return new AgentTurnRecord("t1", "s1", 7L, 1L, TurnKind.START, payload,
        TurnStatus.QUEUED, null, null, null, null, null);
  }

  private AgentTurnExecutor executor() {
    return new AgentTurnExecutor(
        agentRuntime,
        turnRepository,
        eventRepository,
        messageTreeRepository,
        registry,
        observationCollector,
        mock(io.yak.ops.business.agent.memory.MemoryFlushService.class),
        new PassthroughProjectContextScope());
  }

  @Test
  void cancellationDuringRuntimeConstructionIsRememberedAndDisposesLateHandle() throws Exception {
    CountDownLatch constructing = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(turnRepository.cancelRunning("t1")).thenReturn(true);
    when(agentRuntime.stream(anyLong(), anyString(), anyString(), anyString(), anyLong(), any(), any(), any()))
        .thenAnswer(inv -> {
          onComplete = inv.getArgument(6);
          constructing.countDown();
          assertTrue(release.await(2, TimeUnit.SECONDS));
          return (io.yak.ops.business.agent.runtime.TurnSubscription) () -> disposed.countDown();
        });
    var worker = Executors.newSingleThreadExecutor();
    try {
      var execution = worker.submit(() -> executor().executeAndAwait(startRecord()));
      assertTrue(constructing.await(2, TimeUnit.SECONDS));
      registry.cancelBySession("s1");
      verify(turnRepository).cancelRunning("t1");
      release.countDown();
      execution.get(2, TimeUnit.SECONDS);
      assertTrue(disposed.await(2, TimeUnit.SECONDS));
      onComplete.run();
      verify(turnRepository, never()).complete(anyString());
      assertTrue(registry.runningTurnId("s1").isEmpty());
    } finally {
      release.countDown();
      worker.shutdownNow();
    }
  }

  @Test
  void admissionRemainsOccupiedUntilAsynchronousCompletion() throws Exception {
    var worker = Executors.newSingleThreadExecutor();
    try {
      var execution = worker.submit(() -> executor().executeAndAwait(startRecord()));
      assertTrue(subscribed.await(2, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> execution.get(100, TimeUnit.MILLISECONDS));
      onComplete.run();
      execution.get(2, TimeUnit.SECONDS);
      assertTrue(registry.runningTurnId("s1").isEmpty());
    } finally {
      worker.shutdownNow();
    }
  }

  @Test
  void shutdownDisposesInferenceWithoutInventingATerminalOutcome() throws Exception {
    var worker = Executors.newSingleThreadExecutor();
    try {
      var execution = worker.submit(() -> executor().executeAndAwait(startRecord()));
      assertTrue(subscribed.await(2, TimeUnit.SECONDS));
      worker.shutdownNow();
      execution.get(2, TimeUnit.SECONDS);
      assertTrue(disposed.await(2, TimeUnit.SECONDS));
      assertTrue(registry.runningTurnId("s1").isEmpty());
      verify(turnRepository, never()).cancelRunning(anyString());
      verify(turnRepository, never()).complete(anyString());
    } finally {
      worker.shutdownNow();
    }
  }

  @Test
  void normalCompletionMarksTurnAndWritesAssistantNode() {
    AgentTurnRecord record = startRecord();
    executor().execute(record);

    onEvent.accept(ChatTurnEvent.delta(ChatTurnEvent.TurnEventType.TEXT_DELTA, "答案"));
    onComplete.run();

    verify(turnRepository).complete("t1");
    verify(messageTreeRepository).complete(eq("s1"), eq("a1"), contains("答案"), any());
    assertTrue(registry.runningTurnId("s1").isEmpty(), "终态后登记必须清理");
  }

  @Test
  void missingFinishedFrameIsAppendedAsFallbackBeforeCompletion() {
    AgentTurnRecord record = startRecord();
    executor().execute(record);

    onEvent.accept(ChatTurnEvent.delta(ChatTurnEvent.TurnEventType.TEXT_DELTA, "增量"));
    onComplete.run();

    ArgumentCaptor<ChatTurnEvent> frames = ArgumentCaptor.forClass(ChatTurnEvent.class);
    verify(eventRepository, org.mockito.Mockito.atLeastOnce())
        .append(eq("t1"), frames.capture());
    assertEquals(
        List.of(ChatTurnEvent.TurnEventType.TURN_FINISHED),
        frames.getAllValues().stream()
            .filter(f -> f.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED)
            .map(ChatTurnEvent::type)
            .toList(),
        "缺失终帧时必须兜底补一条");
  }

  @Test
  void clarificationSuspendsTurnIntoWaitingInput() {
    AgentTurnRecord record = startRecord();
    executor().execute(record);

    onEvent.accept(
        new ChatTurnEvent(ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED, "营收口径是？",
            "call_1", "clarify_with_user", null, null, null));

    onComplete.run();
    verify(turnRepository).markWaitingInput("t1");
    verify(turnRepository, never()).complete("t1");
    // O3 HITL 观测：挂起事实落 span（toolCallId 与事件帧 join，载荷=澄清问题）
    verify(observationCollector).event(eq("s1"), eq("t1"), eq("HITL"),
        eq("clarify_with_user"), eq("call_1"), eq("COMPLETED"),
        contains("营收口径"), any(), any(), any());
  }

  @Test
  void resumeEmitsHitlSpanWithUserAnswer() {
    when(agentRuntime.resume(anyLong(), anyString(), anyString(), any(), anyLong(), any(), any(), any()))
        .thenAnswer(inv -> {
          onEvent = inv.getArgument(5);
          onComplete = inv.getArgument(6);
          onError = inv.getArgument(7);
          io.yak.ops.business.agent.runtime.TurnSubscription handle = () -> {};
          return handle;
        });
    String payload = TurnInputCodec.encode(TurnInput.ofResume("a1",
        List.of(new io.yak.ops.business.agent.domain.ToolFeedback(
            "call_1", "clarify_with_user", "含税口径，不含退款"))));
    AgentTurnRecord record = new AgentTurnRecord(
        "t1", "s1", 7L, 1L, TurnKind.RESUME, payload, TurnStatus.QUEUED, null, null, null, null, null);

    executor().execute(record);

    // O3 HITL 观测：恢复事实落 span（载荷=用户应答原文——口径沉淀的一等来源）
    verify(observationCollector).event(eq("s1"), eq("t1"), eq("HITL"),
        eq("clarify_with_user"), eq("call_1"), eq("COMPLETED"),
        contains("含税口径"), any(), any(), any());
  }

  @Test
  void providerFailureFailsWithClassifiedCodePartialAnswerPreserved() {
    AgentTurnRecord record = startRecord();
    executor().execute(record);

    onEvent.accept(ChatTurnEvent.delta(ChatTurnEvent.TurnEventType.TEXT_DELTA, "部分"));
    onError.accept(new IllegalStateException("[LLM_PROVIDER_ERROR] 连续失败"));

    verify(turnRepository).fail(eq("t1"), eq("PROVIDER_ERROR"), anyString());
    verify(messageTreeRepository).complete(eq("s1"), eq("a1"), contains("部分"), any());
    var frames = org.mockito.Mockito.mockingDetails(eventRepository).getInvocations().stream()
        .filter(inv -> "append".equals(inv.getMethod().getName()))
        .map(inv -> (ChatTurnEvent) inv.getArgument(1))
        .toList();
    assertTrue(frames.stream().anyMatch(f -> f.type() == ChatTurnEvent.TurnEventType.ERROR),
        "失败帧必须入投递日志: " + frames);
  }

  @Test
  void stopGenerationConvergesToCancelledViaFinalizer() {
    AgentTurnRecord record = startRecord();
    executor().execute(record);
    assertTrue(registry.runningTurnId("s1").isPresent());

    onEvent.accept(ChatTurnEvent.delta(ChatTurnEvent.TurnEventType.TEXT_DELTA, "停前的字"));
    registry.cancelBySession("s1");

    verify(turnRepository).cancelRunning("t1");
    var frames = org.mockito.Mockito.mockingDetails(eventRepository).getInvocations().stream()
        .filter(inv -> "append".equals(inv.getMethod().getName()))
        .map(inv -> (ChatTurnEvent) inv.getArgument(1))
        .toList();
    assertTrue(frames.stream()
            .anyMatch(f -> f.type() == ChatTurnEvent.TurnEventType.TURN_CANCELLED),
        "取消终态帧必须入投递日志");
    verify(messageTreeRepository).complete(eq("s1"), eq("a1"), contains("停前的字"), any());
    assertTrue(registry.runningTurnId("s1").isEmpty());
  }

  @Test
  void failureClosesPendingToolCallsWithAbortedFramesBeforeError() {
    AgentTurnRecord record = startRecord();
    executor().execute(record);

    onEvent.accept(new ChatTurnEvent(
        ChatTurnEvent.TurnEventType.TOOL_CALL, null, "call_9", "run_dataset_query",
        null, null, null));
    onError.accept(new IllegalStateException("[LLM_PROVIDER_ERROR] 失败"));

    var frames = journaledFrames();
    var abortedIdx = -1;
    var errorIdx = -1;
    for (int i = 0; i < frames.size(); i++) {
      ChatTurnEvent f = frames.get(i);
      if (f.type() == ChatTurnEvent.TurnEventType.TOOL_RESULT && "call_9".equals(f.toolCallId())) {
        abortedIdx = i;
      }
      if (f.type() == ChatTurnEvent.TurnEventType.ERROR) {
        errorIdx = i;
      }
    }
    assertTrue(abortedIdx >= 0, "未闭合工具必须补 ABORTED 结果帧");
    assertEquals("ABORTED", frames.get(abortedIdx).toolStatus());
    assertTrue(errorIdx > abortedIdx, "ABORTED 帧必须先于 ERROR 终态帧（前端卡片先闭合）");
  }

  @Test
  void waitingInputKeepsClarifyToolOpenButConvergesStrayCalls() {
    AgentTurnRecord record = startRecord();
    executor().execute(record);

    onEvent.accept(new ChatTurnEvent(
        ChatTurnEvent.TurnEventType.TOOL_CALL, null, "call_stray", "list_datasets",
        null, null, null));
    onEvent.accept(new ChatTurnEvent(
        ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED, "口径是？", "call_clarify",
        "request_clarification", null, null, null));
    onComplete.run();

    verify(turnRepository).markWaitingInput("t1");
    var frames = journaledFrames();
    // 澄清工具不收敛（WAITING_INPUT 合法保持开启）；游离工具收敛为 ABORTED
    assertTrue(frames.stream().noneMatch(f ->
            f.type() == ChatTurnEvent.TurnEventType.TOOL_RESULT && "call_clarify".equals(f.toolCallId())),
        "澄清工具在挂起期不得补终帧");
    assertTrue(frames.stream().anyMatch(f ->
            f.type() == ChatTurnEvent.TurnEventType.TOOL_RESULT
                && "call_stray".equals(f.toolCallId())
                && "ABORTED".equals(f.toolStatus())),
        "挂起前游离工具必须闭合");
  }

  /** 直通 ProjectContextScope：run 直接执行 action，不设 ThreadLocal（测试不依赖项目上下文恢复）。 */
  private static final class PassthroughProjectContextScope implements ProjectContextScope {
    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      return action.get();
    }
  }

  private List<ChatTurnEvent> journaledFrames() {
    return org.mockito.Mockito.mockingDetails(eventRepository).getInvocations().stream()
        .filter(inv -> "append".equals(inv.getMethod().getName()))
        .map(inv -> (ChatTurnEvent) inv.getArgument(1))
        .toList();
  }
}
