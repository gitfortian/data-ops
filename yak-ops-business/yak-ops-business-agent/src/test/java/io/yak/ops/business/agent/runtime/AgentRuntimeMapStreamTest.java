package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** 流映射管道测试：增量播完后，结果事件的整段全文不得重复下发（前端重放 bug 回归）。 */
class AgentRuntimeMapStreamTest {

  private final AgentEventCodec codec = new AgentEventCodec();

  private static AgentEvent textDelta(String text) {
    return new io.agentscope.core.event.TextBlockDeltaEvent("r1", "b1", text);
  }

  private static AgentEvent resultOf(String fullText) {
    Msg result =
        AssistantMessage.builder()
            .content(TextBlock.builder().text(fullText).build())
            .build();
    return new AgentResultEvent(result);
  }

  @Test
  void streamedDeltasAreNotReplayedByResultEvent() {
    Flux<ChatTurnEvent> mapped =
        AgentRuntime.mapStream(
            Flux.just(
                textDelta("我是"),
                textDelta("助手。"),
                resultOf("我是助手。这是重复的全文。"),
                new AgentEndEvent("r1")),
            codec);

    List<ChatTurnEvent> events = mapped.collectList().block(Duration.ofSeconds(5));

    assertEquals(3, events.size());
    assertEquals("我是", events.get(0).delta());
    assertNull(events.get(0).phase(), "流式增量最终性由前端迭代模型判定");
    assertEquals("助手。", events.get(1).delta());
    // 结果事件被去重后，只剩 TURN_FINISHED
    assertEquals(ChatTurnEvent.TurnEventType.TURN_FINISHED, events.get(2).type());
  }

  @Test
  void nonStreamFallbackStillEmitsFullTextOnce() {
    Flux<ChatTurnEvent> mapped =
        AgentRuntime.mapStream(Flux.just(resultOf("一次性完整回答"), new AgentEndEvent("r1")), codec);

    List<ChatTurnEvent> events = mapped.collectList().block(Duration.ofSeconds(5));

    assertEquals(2, events.size());
    assertEquals("一次性完整回答", events.get(0).delta());
  }

  @Test
  void iterationCounterAdvancesAcrossReActCycles() {
    // think1 → tool1 → result1 → think2 → tool2 → result2 → 最终答案
    Flux<ChatTurnEvent> mapped =
        AgentRuntime.mapStream(
            Flux.just(
                new io.agentscope.core.event.ThinkingBlockDeltaEvent("r1", "b1", "想1"),
                toolStart("c1", "tool_a"),
                toolEnd("c1", "tool_a"),
                new io.agentscope.core.event.ThinkingBlockDeltaEvent("r1", "b2", "想2"),
                toolStart("c2", "tool_b"),
                toolEnd("c2", "tool_b"),
                resultOf("最终答案"),
                new AgentEndEvent("r1")),
            codec);

    List<ChatTurnEvent> events = mapped.collectList().block(Duration.ofSeconds(5));

    assertEquals(1, iterOf(events, 0), "首个思考=迭代1");
    assertEquals(1, iterOf(events, 1), "同迭代内工具不递增");
    assertEquals(1, iterOf(events, 2), "结果帧归属发起迭代");
    assertEquals(2, iterOf(events, 3), "结果后的思考=迭代2");
    assertEquals(2, iterOf(events, 5), "迭代2结果帧");
    // 渲染顺序规范 §11.3 规则 2：结果按完成序返回，配对键是 toolCallId
  }

  @Test
  void parallelToolBatchStaysInSameIteration() {
    Flux<ChatTurnEvent> mapped =
        AgentRuntime.mapStream(
            Flux.just(
                toolStart("c1", "tool_a"),
                toolStart("c2", "tool_b"),
                toolEnd("c2", "tool_b"),
                toolEnd("c1", "tool_a")),
            codec);

    List<ChatTurnEvent> events = mapped.collectList().block(Duration.ofSeconds(5));

    assertEquals(1, iterOf(events, 0));
    assertEquals(1, iterOf(events, 1), "并行批同迭代");
    assertEquals(1, iterOf(events, 2));
    assertEquals(1, iterOf(events, 3));
  }

  @Test
  void resultDerivedTextIsMarkedFinalPhase() {
    // 无流式增量的降级路径：结果事件全文一次性下发，且必须携带 FINAL 分层标记
    Flux<ChatTurnEvent> mapped =
        AgentRuntime.mapStream(Flux.just(resultOf("结果全文"), new AgentEndEvent("r1")), codec);

    List<ChatTurnEvent> events = mapped.collectList().block(Duration.ofSeconds(5));

    assertEquals(2, events.size());
    assertEquals("结果全文", events.get(0).delta());
    assertEquals("FINAL", events.get(0).phase(), "结果事件派生全文恒为最终答案");
  }

  private static io.agentscope.core.event.ToolCallStartEvent toolStart(String id, String name) {
    return new io.agentscope.core.event.ToolCallStartEvent("r1", id, name);
  }

  private static io.agentscope.core.event.ToolResultEndEvent toolEnd(String id, String name) {
    return new io.agentscope.core.event.ToolResultEndEvent(
        "r1", id, name, io.agentscope.core.message.ToolResultState.SUCCESS);
  }

  private static int iterOf(List<ChatTurnEvent> events, int index) {
    return events.get(index).iter();
  }
}
