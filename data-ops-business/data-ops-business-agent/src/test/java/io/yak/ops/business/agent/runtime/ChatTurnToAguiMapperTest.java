package io.yak.ops.business.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.event.AguiEvent.Custom;
import io.agentscope.core.agui.event.AguiEvent.ReasoningMessageContent;
import io.agentscope.core.agui.event.AguiEvent.RunError;
import io.agentscope.core.agui.event.AguiEvent.RunFinished;
import io.agentscope.core.agui.event.AguiEvent.RunFinishedSuccessOutcome;
import io.agentscope.core.agui.event.AguiEvent.RunStarted;
import io.agentscope.core.agui.event.AguiEvent.TextMessageContent;
import io.agentscope.core.agui.event.AguiEvent.ToolCallEnd;
import io.agentscope.core.agui.event.AguiEvent.ToolCallResult;
import io.agentscope.core.agui.event.AguiEvent.ToolCallStart;
import io.agentscope.core.agui.event.AguiEventType;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.ChatTurnEvent.TurnEventType;
import io.yak.ops.business.agent.runtime.ChatTurnToAguiMapper.AguiContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 官方 AG-UI 映射测试（官方化 v2 阶段 1）：
 * 10 种领域事件 → 官方 AguiEvent 类型/字段断言；TOOL_RESULT 双帧；rawEvent 扩展透传；ID 映射。
 */
class ChatTurnToAguiMapperTest {

  private final ChatTurnToAguiMapper mapper = new ChatTurnToAguiMapper();
  private final AguiContext ctx =
      new AguiContext("sess-1", "turn-1", "assistant-msg-1");

  private List<AguiEvent> map(ChatTurnEvent event) {
    return mapper.map(ctx, event);
  }

  @Test
  void runStartedMapsThreadAndRunIds() {
    AguiEvent.RunStarted started =
        assertSingle(map(ChatTurnEvent.of(TurnEventType.TURN_STARTED)), AguiEvent.RunStarted.class);
    assertThat(started.getThreadId()).isEqualTo("sess-1");
    assertThat(started.getRunId()).isEqualTo("turn-1");
    assertThat(started.getType()).isEqualTo(AguiEventType.RUN_STARTED);
  }

  @Test
  void thinkingDeltaCarriesDeltaAndExtension() {
    ChatTurnEvent event = ChatTurnEvent.thinkingDelta("先分析口径", 1234L).withIter(2);
    ReasoningMessageContent mapped =
        assertSingle(map(event), ReasoningMessageContent.class);
    assertThat(mapped.delta()).isEqualTo("先分析口径");
    assertThat(mapped.messageId()).isEqualTo("assistant-msg-1");
    assertThat(mapped.rawEvent()).isInstanceOfSatisfying(Map.class, raw -> {
      assertThat(raw).containsEntry("iter", 2).containsEntry("thinkingElapsedMs", 1234L);
    });
  }

  @Test
  void textDeltaCarriesPhaseExtension() {
    ChatTurnEvent event = ChatTurnEvent.delta(TurnEventType.TEXT_DELTA, "结论").withIter(1).withPhase("FINAL");
    TextMessageContent mapped = assertSingle(map(event), TextMessageContent.class);
    assertThat(mapped.delta()).isEqualTo("结论");
    assertThat(mapped.messageId()).isEqualTo("assistant-msg-1");
    assertThat(mapped.rawEvent()).isInstanceOfSatisfying(Map.class, raw -> {
      assertThat(raw).containsEntry("iter", 1).containsEntry("phase", "FINAL");
    });
  }

  @Test
  void toolCallMapsIdsAndStartedAt() {
    ChatTurnEvent event = ChatTurnEvent.tool(TurnEventType.TOOL_CALL, "call_1", "run_dataset_query", null)
        .withIter(1).withStartedAt(1_700_000_000_000L);
    ToolCallStart mapped = assertSingle(map(event), ToolCallStart.class);
    assertThat(mapped.toolCallId()).isEqualTo("call_1");
    assertThat(mapped.toolCallName()).isEqualTo("run_dataset_query");
    assertThat(mapped.rawEvent()).isInstanceOfSatisfying(Map.class, raw -> {
      assertThat(raw).containsEntry("iter", 1).containsEntry("startedAt", 1_700_000_000_000L);
    });
  }

  @Test
  void toolResultEmitsEndThenResultDualFrame() {
    ChatTurnEvent event = ChatTurnEvent.tool(TurnEventType.TOOL_RESULT, "call_1", "run_dataset_query", null)
        .withToolOutcome("{\"rows\":[]}", 250L, "SUCCESS")
        .withIter(1);

    List<AguiEvent> mapped = map(event);
    assertThat(mapped).hasSize(2);
    ToolCallEnd end = assertIs(mapped.get(0), ToolCallEnd.class);
    assertThat(end.toolCallId()).isEqualTo("call_1");
    assertThat(end.rawEvent()).isInstanceOfSatisfying(Map.class, raw -> {
      assertThat(raw).containsEntry("durationMs", 250L).containsEntry("toolStatus", "SUCCESS");
    });
    ToolCallResult result = assertIs(mapped.get(1), ToolCallResult.class);
    assertThat(result.toolCallId()).isEqualTo("call_1");
    assertThat(result.content()).isEqualTo("{\"rows\":[]}");
    assertThat(result.role()).isEqualTo("tool");
    assertThat(result.messageId()).isEqualTo("assistant-msg-1");
  }

  @Test
  void clarifyRequestedMapsToCustomWithValue() {
    // 真实流形状：delta=反问问题文本，toolCallId/toolName 为 pending 工具调用
    ChatTurnEvent event = new ChatTurnEvent(
        TurnEventType.CLARIFY_REQUESTED, "请确认营收口径？", "call_9", "request_clarification",
        null, null, null);
    Custom mapped = assertSingle(map(event), Custom.class);
    assertThat(mapped.name()).isEqualTo("clarify_requested");
    assertThat(mapped.value()).isInstanceOfSatisfying(Map.class, value -> {
      assertThat(value)
          .containsEntry("toolCallId", "call_9")
          .containsEntry("toolName", "request_clarification")
          .containsEntry("question", "请确认营收口径？");
    });
  }

  @Test
  void turnFinishedCarriesElapsedAndTokensExtensions() {
    ChatTurnEvent event = ChatTurnEvent.finished(1200L, 3456L);
    RunFinished mapped = assertSingle(map(event), RunFinished.class);
    assertThat(mapped.getType()).isEqualTo(AguiEventType.RUN_FINISHED);
    assertThat(mapped.outcome()).isInstanceOf(RunFinishedSuccessOutcome.class);
    assertThat(mapped.rawEvent()).isInstanceOfSatisfying(Map.class, raw -> {
      assertThat(raw).containsEntry("elapsedMs", 3456L).containsEntry("totalTokens", 1200L);
    });
  }

  @Test
  void cancelledAndExceededMapToCustom() {
    Custom cancelled = assertSingle(map(ChatTurnEvent.of(TurnEventType.TURN_CANCELLED)), Custom.class);
    assertThat(cancelled.name()).isEqualTo("turn_cancelled");

    Custom exceeded = assertSingle(
        map(new ChatTurnEvent(
            TurnEventType.EXCEEDED_MAX_ITERS, null, null, null, null, "已达最大推理轮数", null)),
        Custom.class);
    assertThat(exceeded.name()).isEqualTo("exceeded_max_iters");
    assertThat(exceeded.value()).isEqualTo("已达最大推理轮数");
  }

  @Test
  void errorMapsToRunErrorWithCode() {
    ChatTurnEvent event = ChatTurnEvent.error("网关超时", "TIMEOUT");
    RunError mapped = assertSingle(map(event), RunError.class);
    assertThat(mapped.message()).isEqualTo("网关超时");
    assertThat(mapped.code()).isEqualTo("TIMEOUT");
  }

  @Test
  void nullMessageIdFallsBackToTurnId() {
    AguiContext noAssistant = new AguiContext("sess-1", "turn-1", null);
    TextMessageContent mapped = assertSingle(
        mapper.map(noAssistant, ChatTurnEvent.delta(TurnEventType.TEXT_DELTA, "x")),
        TextMessageContent.class);
    assertThat(mapped.messageId()).isEqualTo("turn-1");
  }

  @Test
  void extensionOmitsNullFields() {
    // 无 iter/计时等扩展字段时 rawEvent 为 null（NON_NULL 不序列化，控制线上体积）
    TextMessageContent mapped = assertSingle(
        map(ChatTurnEvent.delta(TurnEventType.TEXT_DELTA, "无扩展")), TextMessageContent.class);
    assertThat(mapped.rawEvent()).isNull();
  }

  private static <T extends AguiEvent> T assertSingle(List<AguiEvent> events, Class<T> type) {
    assertThat(events).hasSize(1);
    return assertIs(events.get(0), type);
  }

  private static <T extends AguiEvent> T assertIs(AguiEvent event, Class<T> type) {
    assertThat(event).isInstanceOf(type);
    return type.cast(event);
  }
}
