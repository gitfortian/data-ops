package io.yak.ops.business.agent.domain;

/** 会话历史轮次（只读投影）。消息真相归官方 StateStore，本对象仅为展示映射。 */
public record HistoryTurn(String role, String content) {}
