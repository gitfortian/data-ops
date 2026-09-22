package io.yak.ops.business.agent.domain;

/**
 * 归一化时间轴条目（trace v2）：offsetMs 为相对轮次起点的毫秒偏移；
 * 存量行（无 started_at）为 null，前端按顺序兜底排布。
 */
public record TimelineEntry(Long stepId, String kind, String name, String status, Long offsetMillis, Long durationMillis) {}
