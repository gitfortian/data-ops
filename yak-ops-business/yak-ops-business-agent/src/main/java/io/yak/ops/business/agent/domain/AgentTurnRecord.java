package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;

/** 推理轮次生命周期投影（读模型）。truth owner 是 {@code yak_agent_turn} 表。 */
public record AgentTurnRecord(
    String turnId,
    String sessionId,
    long userId,
    long projectId,
    TurnKind kind,
    String payloadJson,
    TurnStatus status,
    String errorCode,
    String errorMessage,
    LocalDateTime createTime,
    LocalDateTime startTime,
    LocalDateTime endTime) {}
