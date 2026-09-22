package io.yak.ops.business.agent.domain;

/** 轮次输入类型：START 开启新一轮；RESUME 携带工具应答续跑被挂起的同一轮。 */
public enum TurnKind {
  START,
  RESUME
}
