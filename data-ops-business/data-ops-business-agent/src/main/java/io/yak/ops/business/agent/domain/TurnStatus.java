package io.yak.ops.business.agent.domain;

/**
 * 推理轮次生命周期状态。转移白名单见 DOMAIN.md：所有转移走带前置状态的条件 UPDATE，
 * 非法转移影响行数为 0 不算失败。
 */
public enum TurnStatus {
  QUEUED,
  RUNNING,
  WAITING_INPUT,
  COMPLETED,
  FAILED,
  CANCELLED,
  INTERRUPTED;

  /** 终态（订阅端可据此收尾 SSE 流）。 */
  public boolean terminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED || this == INTERRUPTED;
  }
}
