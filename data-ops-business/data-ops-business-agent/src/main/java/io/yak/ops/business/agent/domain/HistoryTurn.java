package io.yak.ops.business.agent.domain;

/** 会话历史只读投影。turnId 是原消息引用，须经 read-side 核对才可关联执行证据。 */
public record HistoryTurn(String role, String content, String turnId) {
  public HistoryTurn(String role, String content) {
    this(role, content, null);
  }
}
