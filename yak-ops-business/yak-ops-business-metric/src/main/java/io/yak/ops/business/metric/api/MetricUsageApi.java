package io.yak.ops.business.metric.api;

import java.util.List;

/**
 * 指标使用上报 SPI（T52, push-based）：消费方引用指标时上报使用事件。
 * 失败语义：fail-open，record()/revoke()/syncBindings() 不得抛出阻断调用方的异常。
 * 对齐 semantic 的 StandardUsageApi。
 */
public interface MetricUsageApi {

  /** 数据集消费方类型(01 消费接线新增,对齐 yak_metric_usage.usage_type)。 */
  String USAGE_TYPE_DATASET = "DATASET";

  /** 记录一次引用事件;实现不得抛出阻断调用方的异常。 */
  void record(MetricUsageEvent event);

  /** 撤销某消费方(usageType+usageId)的全部使用记录;实现不得抛出阻断调用方的异常。 */
  void revoke(String usageType, Long usageId);

  /** 以消费方视角整体同步引用:撤销旧绑定后按 metricIds 重建;实现不得抛出阻断调用方的异常。 */
  void syncBindings(String usageType, Long usageId, String usageName, List<Long> metricIds);

  /** 某消费方当前引用的指标 id 列表(编辑回显用,只读)。 */
  List<Long> boundMetricIds(String usageType, Long usageId);

  /** 单指标的使用统计(服务端聚合)。 */
  UsageSummary summary(Long metricId);

  record MetricUsageEvent(
      Long metricId,
      String usageType,
      Long usageId,
      String usageName,
      Long projectId,
      String operator) {}

  record UsageSummary(
      Long metricId, long totalCount,
      long reportCount, long datasetCount, long dashboardCount,
      long apiCount, long screenCount) {}
}
