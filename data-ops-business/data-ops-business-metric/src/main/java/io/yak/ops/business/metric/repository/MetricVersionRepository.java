package io.yak.ops.business.metric.repository;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.dao.model.MetricVersionPO;
import java.util.List;

/** 指标版本历史仓储接口。 */
public interface MetricVersionRepository {

  void saveSnapshot(Metric metric, String changeDesc, String operator);

  List<MetricVersionPO> listByMetric(Long metricId);

  MetricVersionPO findByMetricAndVersion(Long metricId, int version);

  void deleteByMetric(Long metricId);
}
