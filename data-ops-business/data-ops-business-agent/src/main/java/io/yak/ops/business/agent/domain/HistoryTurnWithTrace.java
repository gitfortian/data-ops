package io.yak.ops.business.agent.domain;

import java.util.List;

/**
 * 带 trace 的会话历史轮次：在 HistoryTurn 基础上扩展思考/工具调用链路。
 * turnId（可空）：assistant 通过原消息引用核对的完成轮次 ID（error 为独立失败记录 ID），
 * 前端据此按需懒加载 trace v2 权威视图（刷新后观测数据不丢）。
 */
public record HistoryTurnWithTrace(
    String role, String content, String turnId, List<HistoryTraceStep> trace) {}
