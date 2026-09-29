package io.yak.ops.business.agent.domain;

/** 用户对挂起反问的应答：toolCallId 必须与框架 pending 状态匹配，否则恢复被拒绝。 */
public record ToolFeedback(String toolCallId, String toolName, String output) {}
