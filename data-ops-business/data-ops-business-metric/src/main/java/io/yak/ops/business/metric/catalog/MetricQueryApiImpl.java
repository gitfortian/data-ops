package io.yak.ops.business.metric.catalog;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.common.api.metric.MetricQueryApi;
import io.yak.ops.common.api.metric.MetricQueryView;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 指标只读 SPI 实现(ticket 60):供建模 DWS/ADS 反推消费。
 * 仅返回启用指标;project 范围由仓储按可信上下文约束。
 */
@Component
public class MetricQueryApiImpl implements MetricQueryApi {

  private final MetricRepository repository;

  public MetricQueryApiImpl(MetricRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<MetricQueryView> listEnabledByIds(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return repository.listByIds(List.copyOf(ids)).stream()
        .filter(metric -> metric.status() == MetricStatus.ENABLED)
        .map(MetricQueryApiImpl::toView)
        .toList();
  }

  private static MetricQueryView toView(Metric metric) {
    return new MetricQueryView(
        metric.id(),
        metric.metricCode(),
        metric.metricName(),
        metric.metricType() == null ? null : metric.metricType().name(),
        metric.processId(),
        metric.caliberId(),
        metric.measureExpr(),
        metric.filterExpr(),
        metric.dimModelIds(),
        metric.refMetricId(),
        metric.modelId(),
        metric.statDimensions(),
        metric.statPeriod() == null ? null : metric.statPeriod().name());
  }
}
