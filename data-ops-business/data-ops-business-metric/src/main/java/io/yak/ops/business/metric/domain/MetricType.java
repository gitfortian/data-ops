package io.yak.ops.business.metric.domain;

/** 指标类型。 */
public enum MetricType {
  /** 原子指标,直接聚合。 */
  ATOMIC,
  /** 派生指标,原子 + 维度限定。 */
  DERIVED,
  /** 复合指标,多指标运算。 */
  COMPOSITE
}
