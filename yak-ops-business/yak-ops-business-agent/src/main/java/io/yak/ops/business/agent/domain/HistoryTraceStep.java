package io.yak.ops.business.agent.domain;

/** 历史 trace 步骤（历史回放用）：从事件日志重建的思考/工具调用链路。 */
public record HistoryTraceStep(
    String kind,
    String text,
    String toolCallId,
    String toolName,
    String resultText) {}
