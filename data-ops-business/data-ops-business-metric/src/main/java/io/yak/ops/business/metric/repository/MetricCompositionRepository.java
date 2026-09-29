package io.yak.ops.business.metric.repository;

import io.yak.ops.business.metric.api.MetricApi;
import io.yak.ops.common.bean.po.metric.MetricCompositionPO;
import java.util.List;

/** 复合指标组成仓储接口。 */
public interface MetricCompositionRepository {

  void replaceCompositions(Long metricId, List<MetricApi.CompositionItem> compositions);

  void deleteByMetric(Long metricId);

  List<MetricCompositionPO> listByMetric(Long metricId);

  long countBySubMetric(Long subMetricId);
}
