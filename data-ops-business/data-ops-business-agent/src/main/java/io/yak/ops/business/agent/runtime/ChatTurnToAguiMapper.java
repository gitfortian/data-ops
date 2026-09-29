package io.yak.ops.business.agent.runtime;

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
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 领域 {@link ChatTurnEvent} → 官方 AG-UI {@link AguiEvent} 映射（官方化 v2 协议路径）。
 *
 * <p>纯函数、无状态：实时发布（executor 回调）与日志重放（tailer 从 {@code yak_agent_turn_event}
 * 拉取）走<b>同一映射</b>，保证线上帧字节一致（断线续播无重复无丢失不变式）。</p>
 *
 * <p>设计契约（见 {@code docs/agent/agent-agui-official-v2-plan.md} §二）：</p>
 * <ul>
 *   <li>ID 映射：{@code threadId=sessionId}、{@code runId=turnId}、{@code messageId=assistantMessageId}；</li>
 *   <li>{@code TOOL_RESULT} 产两帧：{@code TOOL_CALL_END}（调用闭合，携带耗时/状态扩展）+
 *       {@code TOOL_CALL_RESULT}（结果内容）——官方语义；</li>
 *   <li>yak-ops 扩展字段（iter/phase/思考与工具计时/tokens/errorCode）经官方 {@code rawEvent}
 *       槽透传，官方 record 面保持干净、AG-UI 生态可解析；</li>
 *   <li>HITL/取消/超迭代（非标准事件）用官方 {@code CUSTOM} 扩展点承载
 *       （{@code name=clarify_requested|turn_cancelled|exceeded_max_iters}）。</li>
 * </ul>
 */
@ConditionalOnAgentEnabled
@Component
public class ChatTurnToAguiMapper {

  /** AG-UI 上下文：会话/轮次/assistant 消息 ID（ID 映射契约）。 */
  public record AguiContext(String sessionId, String turnId, String assistantMessageId) {}

  /**
   * 把一帧领域事件映射为官方 AG-UI 事件列表。
   *
   * @param ctx   AG-UI 上下文（threadId/runId/messageId 来源）
   * @param event 领域事件帧（真相路径产出，富化 iter/phase/服务端计时）
   * @return 官方 AG-UI 事件（TOOL_RESULT 为两帧，其余单帧；恒非空）
   */
  public List<AguiEvent> map(AguiContext ctx, ChatTurnEvent event) {
    String threadId = ctx.sessionId();
    String runId = ctx.turnId();
    String messageId = ctx.assistantMessageId() != null ? ctx.assistantMessageId() : runId;
    return switch (event.type()) {
      case TURN_STARTED ->
          List.of(new RunStarted(threadId, runId));
      case THINKING_DELTA ->
          List.of(new ReasoningMessageContent(
              threadId, runId, messageId, event.delta(), null,
              extension("iter", event.iter(), "thinkingElapsedMs", event.thinkingElapsedMs())));
      case TEXT_DELTA ->
          List.of(new TextMessageContent(
              threadId, runId, messageId, event.delta(), null,
              extension("iter", event.iter(), "phase", event.phase())));
      case TOOL_CALL ->
          List.of(new ToolCallStart(
              threadId, runId, event.toolCallId(), event.toolName(), null,
              extension("iter", event.iter(), "startedAt", event.startedAt())));
      case TOOL_RESULT -> List.of(
          new ToolCallEnd(
              threadId, runId, event.toolCallId(), null,
              extension(
                  "iter", event.iter(),
                  "durationMs", event.durationMs(),
                  "toolStatus", event.toolStatus())),
          new ToolCallResult(
              threadId, runId, event.toolCallId(), event.toolResult(), "tool", messageId));
      case CLARIFY_REQUESTED -> {
        // 澄清问题语义：question=delta（框架 ToolUseBlock.content），与领域 CLARIFY 帧一致；
        // 用 LinkedHashMap 允许字段为 null（Map.of 拒 null）
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("toolCallId", event.toolCallId());
        value.put("toolName", event.toolName());
        value.put("question", event.delta());
        yield List.of(new Custom(threadId, runId, "clarify_requested", value));
      }
      case TURN_FINISHED ->
          List.of(new RunFinished(
              threadId, runId, null, new RunFinishedSuccessOutcome(), null,
              extension("elapsedMs", event.elapsedMs(), "totalTokens", event.totalTokens())));
      case TURN_CANCELLED ->
          List.of(new Custom(threadId, runId, "turn_cancelled", null));
      case EXCEEDED_MAX_ITERS ->
          List.of(new Custom(threadId, runId, "exceeded_max_iters", event.errorMessage()));
      case ERROR ->
          List.of(new RunError(threadId, runId, event.errorMessage(), event.errorCode()));
    };
  }

  /** 扩展载荷：只收纳非空字段（控制线上体积）；全空返回 null（官方 NON_NULL 不序列化）。 */
  private static Map<String, Object> extension(Object... keyValues) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      Object value = keyValues[i + 1];
      if (value != null) {
        map.put((String) keyValues[i], value);
      }
    }
    return map.isEmpty() ? null : map;
  }
}
