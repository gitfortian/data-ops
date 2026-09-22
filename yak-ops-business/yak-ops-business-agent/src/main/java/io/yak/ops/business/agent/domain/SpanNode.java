package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * trace v2 span 节点（读模型）：步骤事实 + 解码后载荷 + 树形子节点。
 * flat 视图与 tree 视图共用本记录（children 空即叶子）。
 * 失败语义归一（设计稿 §7.4）：failureDetail 优先取工具业务错误结构，
 * errorMessage 模板句仅作兜底。
 */
public record SpanNode(
    Long id,
    String kind,
    String name,
    String status,
    String toolCallId,
    Long parentStepId,
    Integer attempt,
    Long durationMillis,
    Integer promptTokens,
    Integer completionTokens,
    Integer retryCount,
    String errorCode,
    String errorMessage,
    String failureDetail,
    StepPayload request,
    StepPayload response,
    LocalDateTime createTime,
    List<SpanNode> children) {}
