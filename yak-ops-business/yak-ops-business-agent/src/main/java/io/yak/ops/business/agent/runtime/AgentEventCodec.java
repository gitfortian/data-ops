package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AgentScope 事件 -> 领域轮次事件的防腐映射。
 * 必须穷尽已知事件类型；未知事件降级为忽略并记录 debug，不静默崩溃。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
public class AgentEventCodec {

  public ChatTurnEvent map(AgentEvent event) {
    if (event instanceof TextBlockDeltaEvent delta) {
      return ChatTurnEvent.delta(ChatTurnEvent.TurnEventType.TEXT_DELTA, delta.getDelta());
    }
    if (event instanceof ThinkingBlockDeltaEvent thinking) {
      return ChatTurnEvent.delta(ChatTurnEvent.TurnEventType.THINKING_DELTA, thinking.getDelta());
    }
    if (event instanceof ToolCallStartEvent toolCall) {
      return ChatTurnEvent.tool(
          ChatTurnEvent.TurnEventType.TOOL_CALL, toolCall.getToolCallId(), toolCall.getToolCallName(), null);
    }
    if (event instanceof ToolResultEndEvent toolResult) {
      return ChatTurnEvent.tool(
          ChatTurnEvent.TurnEventType.TOOL_RESULT,
          toolResult.getToolCallId(),
          toolResult.getToolCallName(),
          null);
    }
    if (event instanceof ToolResultTextDeltaEvent ignored) {
      // 工具结果增量对用户无意义，聚合后的终态由 ToolResultEndEvent 表达
      return null;
    }
    if (event instanceof AgentResultEvent result) {
      return mapResult(result.getResult());
    }
    if (event instanceof AgentEndEvent ignored) {
      return ChatTurnEvent.of(ChatTurnEvent.TurnEventType.TURN_FINISHED);
    }
    if (event instanceof ExceedMaxItersEvent exceeded) {
      return new ChatTurnEvent(
          ChatTurnEvent.TurnEventType.EXCEEDED_MAX_ITERS,
          null,
          null,
          null,
          null,
          "已达最大推理轮数 " + exceeded.getCurrentIter() + "/" + exceeded.getMaxIters(),
          null);
    }
    log.debug("ignored agent event: {}", event.getClass().getSimpleName());
    return null;
  }

  /** 结果消息分流：挂起轮（HITL 反问）映射为 CLARIFY_REQUESTED，普通轮映射为正文增量。 */
  private ChatTurnEvent mapResult(io.agentscope.core.message.Msg result) {
    if (result != null
        && result.getGenerateReason() == io.agentscope.core.message.GenerateReason.TOOL_SUSPENDED) {
      var pendingCalls = result.getContentBlocks(io.agentscope.core.message.ToolUseBlock.class);
      if (pendingCalls.size() > 1) {
        log.warn("suspended turn carries {} pending tool calls; only the first is surfaced",
            pendingCalls.size());
      }
      if (!pendingCalls.isEmpty()) {
        var call = pendingCalls.get(0);
        return new ChatTurnEvent(
            ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED,
            call.getContent(),
            call.getId(),
            call.getName(),
            null,
            null,
            null);
      }
    }
    return ChatTurnEvent.delta(ChatTurnEvent.TurnEventType.TEXT_DELTA, textOf(result));
  }

  /** 从结果消息提取纯文本。 */
  static String textOf(io.agentscope.core.message.Msg message) {
    if (message == null) {
      return "";
    }
    String text = message.getTextContent();
    return text == null ? "" : text;
  }
}
