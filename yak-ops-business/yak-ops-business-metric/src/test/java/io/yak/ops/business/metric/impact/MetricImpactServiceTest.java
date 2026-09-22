package io.yak.ops.business.metric.impact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.repository.MetricDependencyRepository;
import io.yak.ops.common.bean.po.metric.MetricDependencyPO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 反向影响分析单测:METRIC 别名合并、同指标多类命中去重、缺失指标剔除。 */
class MetricImpactServiceTest {

  private MetricDependencyRepository dependencyRepository;
  private MetricReferenceResolver referenceResolver;
  private MetricImpactService service;

  @BeforeEach
  void setUp() {
    dependencyRepository = mock(MetricDependencyRepository.class);
    referenceResolver = mock(MetricReferenceResolver.class);
    service = new MetricImpactService(
        mock(MetricCatalogService.class), dependencyRepository, mock(MetricUsageApi.class), referenceResolver);
  }

  @Test
  void metricAliasMergesRefAndCompositionRows() {
    when(dependencyRepository.listByDependency("REF_METRIC", 7L))
        .thenReturn(List.of(dep(101L, "REF_METRIC", 3)));
    when(dependencyRepository.listByDependency("COMPOSITION", 7L))
        .thenReturn(List.of(dep(101L, "COMPOSITION", 2), dep(102L, "COMPOSITION", 5)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of(101L, metric(101L), 102L, metric(102L)));

    List<MetricImpactService.AffectedMetric> affected = service.findAffectedMetrics("METRIC", 7L);

    assertThat(affected).hasSize(2);
    MetricImpactService.AffectedMetric merged = affected.stream()
        .filter(row -> row.metricId().equals(101L)).findFirst().orElseThrow();
    assertThat(merged.dependencyTypes()).containsExactly("COMPOSITION", "REF_METRIC");
    assertThat(merged.registeredVersion()).isEqualTo(2);
  }

  @Test
  void removedMetricsAreSkipped() {
    when(dependencyRepository.listByDependency(eq("MODEL"), any()))
        .thenReturn(List.of(dep(101L, "MODEL", 1)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of());

    assertThat(service.findAffectedMetrics("MODEL", 9L)).isEmpty();
  }

  @Test
  void blankArgsReturnEmptyWithoutQuery() {
    assertThat(service.findAffectedMetrics(null, 1L)).isEmpty();
    assertThat(service.findAffectedMetrics("  ", 1L)).isEmpty();
    assertThat(service.findAffectedMetrics("MODEL", null)).isEmpty();
    verify(dependencyRepository, never()).listByDependency(any(), any());
  }

  private static MetricDependencyPO dep(Long metricId, String type, Integer version) {
    MetricDependencyPO po = new MetricDependencyPO();
    po.setMetricId(metricId);
    po.setDependencyType(type);
    po.setDependencyId(7L);
    po.setDependencyVersion(version);
    return po;
  }

  private static Metric metric(Long id) {
    return new Metric(id, "M_" + id, "指标" + id, null, null, MetricType.DERIVED,
        null, null, null, null, null, null, null, null, null, null, null, null, null,
        "tester", MetricStatus.ENABLED, 1, null, null, null, null);
  }
}
