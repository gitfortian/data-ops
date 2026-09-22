package io.yak.ops.business.agent.domain;

import java.util.List;

/**
 * 轮次输入投影（持久化于 turn.payload_json）。START 携带用户消息；RESUME 携带与
 * pending 匹配的工具应答。消息树节点 ID 在提交时冻结，挂起-恢复跨执行复用同一对节点。
 */
public record TurnInput(
    String userMessageId,
    String assistantMessageId,
    String message,
    List<ToolFeedback> feedbacks) {

  public static TurnInput ofStart(String userMessageId, String assistantMessageId, String message) {
    return new TurnInput(userMessageId, assistantMessageId, message, List.of());
  }

  public static TurnInput ofResume(String assistantMessageId, List<ToolFeedback> feedbacks) {
    return new TurnInput(null, assistantMessageId, null, List.copyOf(feedbacks));
  }
}
