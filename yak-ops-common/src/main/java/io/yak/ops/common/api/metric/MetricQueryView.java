package io.yak.ops.common.api.metric;

/**
 * 指标只读视图(供建模反推;类型/周期用字符串,与 common 的 MetricPO 一致,
 * 避免 SPI 依赖 metric 域模块的枚举造成循环依赖)。
 *
 * @param metricType ATOMIC/DERIVED/COMPOSITE
 * @param measureExpr 度量表达式,如 SUM(order_amount) / COUNT(DISTINCT order_id)
 * @param dimModelIds DIM 模型引用(JSON 数组)
 * @param modelId 依赖模型(引用 modeling,松散 ID;DWS 反推即上游 DWD/DWS)
 * @param statDimensions 统计维度(JSON 数组)
 * @param statPeriod 统计周期:DAY/WEEK/MONTH
 */
public record MetricQueryView(
    Long id,
    String metricCode,
    String metricName,
    String metricType,
    Long processId,
    Long caliberId,
    String measureExpr,
    String filterExpr,
    String dimModelIds,
    Long refMetricId,
    Long modelId,
    String statDimensions,
    String statPeriod) {}
