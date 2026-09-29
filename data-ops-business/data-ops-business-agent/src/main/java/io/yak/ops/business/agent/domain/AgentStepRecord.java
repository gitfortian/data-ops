package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;

/**
 * 步骤级执行事实（读模型）。truth owner 是 {@code yak_agent_step} 表；
 * 轮次内按 id 升序即执行序，parentStepId 构成 turn 内 trace 父子链。
 * startedAt/endedAt/attempt 为 V9 升列（存量行为 null，读取侧回退 stats_json.latencyMs）。
 */
public record AgentStepRecord(
    Long id,
    String turnId,
    Long parentStepId,
    String kind,
    String name,
    String toolCallId,
    String status,
    String requestJson,
    String responseJson,
    String errorCode,
    String errorMessage,
    String statsJson,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Integer attempt,
    LocalDateTime createTime) {

  /** 轮次汇总步骤 kind（与 AgentStepRecorder 常量同值；read 侧引用收敛在本记录，避免跨层边）。 */
  public static final String KIND_TURN_SUMMARY = "TURN_SUMMARY";
}