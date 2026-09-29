package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轮次 trace 读模型 v2（设计稿 §7.1）：truth 来自 {@code yak_agent_turn}（生命周期）
 * 与 {@code yak_agent_step}（步骤事实）。在 v1 平铺 steps 基础上新增四视图：
 * 树（parent 链组装）、归一化时间轴、kind 聚合、链路完整性（I9 断链显式化）；
 * kinds 为注册表渲染投影（前端数据驱动渲染依据）。
 * VO 组装归属 controller（对齐既有分层）。
 */
public record TurnTraceView(
    String turnId,
    String sessionId,
    String status,
    String errorCode,
    String errorMessage,
    Long totalTokens,
    Long elapsedMillis,
    LocalDateTime createTime,
    LocalDateTime startTime,
    LocalDateTime endTime,
    List<SpanNode> tree,
    List<SpanNode> spans,
    List<TimelineEntry> timeline,
    List<KindAggregate> aggregates,
    TraceCompleteness completeness,
    List<KindMeta> kinds) {}
