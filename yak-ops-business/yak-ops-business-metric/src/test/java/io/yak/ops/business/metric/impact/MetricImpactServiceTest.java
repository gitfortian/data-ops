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

/** 指标影响分析单测:依赖健康语义、Authoring next step、METRIC 别名合并与缺失处理。 */
class MetricImpactServiceTest {

  private MetricCatalogService catalogService;
  private MetricDependencyRepository dependencyRepository;
  private MetricUsageApi usageApi;
  private MetricReferenceResolver referenceResolver;
  private MetricImpactService service;

  @BeforeEach
  void setUp() {
    catalogService = mock(MetricCatalogService.class);
    dependencyRepository = mock(MetricDependencyRepository.class);
    usageApi = mock(MetricUsageApi.class);
    referenceResolver = mock(MetricReferenceResolver.class);
    service = new MetricImpactService(
        catalogService, dependencyRepository, usageApi, referenceResolver);
  }

  @Test
  void checkUpstreamChangesExposesStableDependencyHealthWithoutBreakingLegacyStatus() {
    long metricId = 100L;
    when(catalogService.get(metricId)).thenReturn(metric(metricId, 5));
    when(dependencyRepository.listByMetric(metricId)).thenReturn(List.of(
        upstreamDep(7L, "REF_METRIC", "metric_7", 3),
        upstreamDep(8L, "COMPOSITION", "metric_8", 3),
        upstreamDep(9L, "REF_METRIC", "metric_9", 3),
        upstreamDep(10L, "CALIBER", "caliber_10", 3)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of(
        7L, metric(7L, 3),
        8L, metric(8L, 4)));
    when(referenceResolver.standardReferenceResolution(10L))
        .thenReturn(MetricReferenceResolver.ReferenceResolution.unavailable());
    stubUsage(metricId);

    MetricImpactService.ImpactReport report = service.checkUpstreamChanges(metricId);

    assertThat(report.changes()).hasSize(4);

    MetricImpactService.DependencyChange upToDate = change(report, "metric_7");
    assertThat(upToDate.dependencyHealth())
        .isEqualTo(MetricImpactService.DependencyHealth.UP_TO_DATE);
    assertThat(upToDate.changeStatus()).isEqualTo("UNCHANGED");

    MetricImpactService.DependencyChange outdated = change(report, "metric_8");
    assertThat(outdated.dependencyHealth())
        .isEqualTo(MetricImpactService.DependencyHealth.OUTDATED);
    assertThat(outdated.changeStatus()).isEqualTo("CHANGED");

    MetricImpactService.DependencyChange removed = change(report, "metric_9");
    assertThat(removed.dependencyHealth())
        .isEqualTo(MetricImpactService.DependencyHealth.REMOVED);
    assertThat(removed.changeStatus()).isEqualTo("MISSING");

    MetricImpactService.DependencyChange unavailable = change(report, "caliber_10");
    assertThat(unavailable.dependencyHealth())
        .isEqualTo(MetricImpactService.DependencyHealth.UNAVAILABLE);
    assertThat(unavailable.changeStatus()).isEqualTo("UNKNOWN");

    assertThat(report.authoringNextStep())
        .isEqualTo(MetricImpactService.AuthoringNextStep.RESOLVE_REMOVED_DEPENDENCY);
  }

  @Test
  void dependencyContextDoesNotDependOnUsageOrReloadMetric() {
    long metricId = 100L;
    Metric current = metric(metricId, 5);
    when(dependencyRepository.listByMetric(metricId))
        .thenReturn(List.of(upstreamDep(7L, "REF_METRIC", "metric_7", 3)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of(7L, metric(7L, 3)));

    MetricImpactService.DependencyContext context = service.dependencyContext(current);

    assertThat(context.changes()).hasSize(1);
    assertThat(context.authoringNextStep())
        .isEqualTo(MetricImpactService.AuthoringNextStep.VALIDATE);
    verify(usageApi, never()).summary(any());
    verify(catalogService, never()).get(any());
  }

  @Test
  void removedCrossDomainDependencyIsDistinctFromProviderUnavailable() {
    long metricId = 100L;
    when(catalogService.get(metricId)).thenReturn(metric(metricId, 5));
    when(dependencyRepository.listByMetric(metricId)).thenReturn(List.of(
        upstreamDep(20L, "MODEL", "model_20", 3),
        upstreamDep(30L, "UNIT", "unit_30", 1)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of());
    when(referenceResolver.modelReferenceResolution(20L))
        .thenReturn(MetricReferenceResolver.ReferenceResolution.removed());
    when(referenceResolver.standardReferenceResolution(30L))
        .thenReturn(MetricReferenceResolver.ReferenceResolution.unavailable());
    stubUsage(metricId);

    MetricImpactService.ImpactReport report = service.checkUpstreamChanges(metricId);

    assertThat(change(report, "model_20").dependencyHealth())
        .isEqualTo(MetricImpactService.DependencyHealth.REMOVED);
    assertThat(change(report, "unit_30").dependencyHealth())
        .isEqualTo(MetricImpactService.DependencyHealth.UNAVAILABLE);
    assertThat(report.authoringNextStep())
        .isEqualTo(MetricImpactService.AuthoringNextStep.RESOLVE_REMOVED_DEPENDENCY);
  }

  @Test
  void healthyDependenciesDirectAuthoringToValidation() {
    long metricId = 100L;
    when(catalogService.get(metricId)).thenReturn(metric(metricId, 5));
    when(dependencyRepository.listByMetric(metricId))
        .thenReturn(List.of(upstreamDep(7L, "REF_METRIC", "metric_7", 3)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of(7L, metric(7L, 3)));
    stubUsage(metricId);

    MetricImpactService.ImpactReport report = service.checkUpstreamChanges(metricId);

    assertThat(report.authoringNextStep())
        .isEqualTo(MetricImpactService.AuthoringNextStep.VALIDATE);
  }

  @Test
  void outdatedDependencyMustBeReviewedBeforeValidation() {
    long metricId = 100L;
    when(catalogService.get(metricId)).thenReturn(metric(metricId, 5));
    when(dependencyRepository.listByMetric(metricId))
        .thenReturn(List.of(upstreamDep(7L, "REF_METRIC", "metric_7", 2)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of(7L, metric(7L, 3)));
    stubUsage(metricId);

    MetricImpactService.ImpactReport report = service.checkUpstreamChanges(metricId);

    assertThat(report.authoringNextStep())
        .isEqualTo(MetricImpactService.AuthoringNextStep.REVIEW_OUTDATED_DEPENDENCY);
  }

  @Test
  void unavailableDependencyProviderDirectsUserToRetryInsteadOfValidation() {
    long metricId = 100L;
    when(catalogService.get(metricId)).thenReturn(metric(metricId, 5));
    when(dependencyRepository.listByMetric(metricId))
        .thenReturn(List.of(upstreamDep(20L, "MODEL", "model_20", 2)));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of());
    when(referenceResolver.modelReferenceResolution(20L))
        .thenReturn(MetricReferenceResolver.ReferenceResolution.unavailable());
    stubUsage(metricId);

    MetricImpactService.ImpactReport report = service.checkUpstreamChanges(metricId);

    assertThat(report.authoringNextStep())
        .isEqualTo(MetricImpactService.AuthoringNextStep.RETRY_DEPENDENCY_PROVIDER);
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

  private void stubUsage(long metricId) {
    when(usageApi.summary(metricId))
        .thenReturn(new MetricUsageApi.UsageSummary(metricId, 0, 0, 0, 0, 0, 0));
  }

  private static MetricImpactService.DependencyChange change(
      MetricImpactService.ImpactReport report, String dependencyCode) {
    return report.changes().stream()
        .filter(row -> dependencyCode.equals(row.dependencyCode()))
        .findFirst()
        .orElseThrow();
  }

  private static MetricDependencyPO upstreamDep(
      Long dependencyId, String type, String code, Integer version) {
    MetricDependencyPO po = new MetricDependencyPO();
    po.setMetricId(100L);
    po.setDependencyType(type);
    po.setDependencyId(dependencyId);
    po.setDependencyCode(code);
    po.setDependencyVersion(version);
    return po;
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
    return metric(id, 1);
  }

  private static Metric metric(Long id, int version) {
    return new Metric(id, "M_" + id, "指标" + id, null, null, MetricType.DERIVED,
        null, null, null, null, null, null, null, null, null, null, null, null, null,
        "tester", MetricStatus.ENABLED, version, null, null, null, null);
  }
}
