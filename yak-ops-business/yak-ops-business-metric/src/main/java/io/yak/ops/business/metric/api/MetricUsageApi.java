package io.yak.ops.business.metric.api;

import java.util.List;

/**
 * Metric Reference Usage SPI: consumers report when they save a Metric reference.
 * 失败语义：fail-open，record()/revoke()/syncBindings() 不得抛出阻断调用方的异常。
 * 对齐 semantic 的 StandardUsageApi。
 */
public interface MetricUsageApi {

  /** Dataset consumer type matching yak_metric_usage.usage_type. */
  String USAGE_TYPE_DATASET = "DATASET";

  /** 记录一次引用事件;实现不得抛出阻断调用方的异常。 */
  void record(MetricUsageEvent event);

  /** 撤销某消费方(usageType+usageId)的全部使用记录;实现不得抛出阻断调用方的异常。 */
  void revoke(String usageType, Long usageId);

  /** 以消费方视角整体同步引用:撤销旧绑定后按 metricIds 重建;实现不得抛出阻断调用方的异常。 */
  void syncBindings(String usageType, Long usageId, String usageName, List<Long> metricIds);

  /** Replace governed references after verifying every exact version is currently published. */
  void syncPublishedBindings(
      String usageType, Long usageId, String usageName, List<MetricVersionRef> references);

  /** 某消费方当前引用的指标 id 列表(编辑回显用,只读)。 */
  List<Long> boundMetricIds(String usageType, Long usageId);

  /** Returns exact versions for governed references and null versions for legacy references. */
  List<MetricVersionRef> boundMetricVersionRefs(String usageType, Long usageId);

  record MetricVersionRef(Long metricId, Integer versionNo) {}

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
