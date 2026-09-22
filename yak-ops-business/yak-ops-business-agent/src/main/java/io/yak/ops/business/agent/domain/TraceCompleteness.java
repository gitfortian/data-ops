package io.yak.ops.business.agent.domain;

import java.util.List;

/**
 * 链路完整性（I9：未观测到 ≠ 成功）：断链模式显式化，不静默脑补为完整。
 * reason 约定：missing_turn_summary / orphan_tool_call:{toolCallId} / empty_steps。
 */
public record TraceCompleteness(boolean complete, List<String> reasons) {}
