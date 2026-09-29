package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 事件防腐映射测试：普通结果 vs HITL 挂起结果（CLARIFY_REQUESTED）。 */
class AgentEventCodecTest {

  private final AgentEventCodec codec = new AgentEventCodec();

  @Test
  void normalResultMapsToTextDelta() {
    Msg result =
        AssistantMessage.builder()
            .content(TextBlock.builder().text("结论：销售额上升。").build())
            .build();
    AgentEvent event = new AgentResultEvent(result);

    ChatTurnEvent mapped = codec.map(event);

    assertEquals(ChatTurnEvent.TurnEventType.TEXT_DELTA, mapped.type());
    assertEquals("结论：销售额上升。", mapped.delta());
  }

  @Test
  void suspendedResultWithoutBlocksFallsBackToTextDelta() {
    Msg suspended =
        AssistantMessage.builder().generateReason(GenerateReason.TOOL_SUSPENDED).build();
    AgentEvent event = new AgentResultEvent(suspended);

    ChatTurnEvent mapped = codec.map(event);

    // 无 ToolUseBlock 的异常挂起消息兜底为正文路径，不产生无法应答的 CLARIFY 帧
    assertEquals(ChatTurnEvent.TurnEventType.TEXT_DELTA, mapped.type());
  }

  @Test
  void unknownEventIsIgnoredWithoutFailure() {
    assertEquals(null, codec.map(new io.agentscope.core.event.CustomEvent("r1", Map.of())));
    assertEquals(
        null,
        codec.map(new io.agentscope.core.event.AgentStartEvent("u1", "s1", "r1", "agent", null, null)));
  }
}
