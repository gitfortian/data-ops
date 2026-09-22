package io.yak.ops.business.agent.conversation;

/**
 * 轮次状态冲突（Phase 3）：HITL 重复 resolve / 状态已被并发改变。
 * 映射 HTTP 409 Conflict（区别于参数错误 400），客户端可据此刷新会话状态后重试。
 */
public class TurnConflictException extends RuntimeException {

  public TurnConflictException(String message) {
    super(message);
  }
}
