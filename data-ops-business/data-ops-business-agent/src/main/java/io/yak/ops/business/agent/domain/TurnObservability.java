package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;
import java.util.List;

/** 会话级观测矩阵行（设计稿 §7.2）：一个轮次 × 各 kind 聚合。 */
public record TurnObservability(
    String turnId,
    String status,
    LocalDateTime createTime,
    Long elapsedMillis,
    Long totalTokens,
    List<KindAggregate> byKind) {}
