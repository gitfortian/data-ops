package io.yak.ops.business.metric.repository;

import io.yak.ops.business.metric.domain.MetricUsage;
import java.util.List;

/** Project-scoped persistence boundary for downstream Metric references. */
public interface MetricUsageRepository {

  void append(Long projectId, MetricUsage usage);

  void deleteForConsumer(String usageType, Long usageId);

  List<MetricUsage> listForConsumer(String usageType, Long usageId);

  List<MetricUsage> listByMetric(Long metricId);

  /** Stable limited slice for evidence review; limit must be between 1 and 21. */
  List<MetricUsage> listByMetricBounded(Long metricId, int limit);

  long countByMetric(Long metricId);

  List<UsageTypeCount> countGroupByType(Long metricId);

  void deleteByMetric(Long metricId);

  record UsageTypeCount(String usageType, long count) {}
}
