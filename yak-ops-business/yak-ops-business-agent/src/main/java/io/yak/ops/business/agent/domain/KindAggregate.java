package io.yak.ops.business.agent.domain;

/** kind 维度聚合（trace v2 / 会话级观测矩阵共用）：count/耗时/token 统计。 */
public record KindAggregate(
    String kind,
    long count,
    long totalMillis,
    long maxMillis,
    long avgMillis,
    long p95Millis,
    Long promptTokens,
    Long completionTokens) {}
