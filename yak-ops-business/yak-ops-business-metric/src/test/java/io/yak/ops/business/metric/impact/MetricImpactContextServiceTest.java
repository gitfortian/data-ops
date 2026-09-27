package io.yak.ops.business.metric.impact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricImpactContextServiceTest {

  @Test
  void keepsReferenceUsageSeparateAndSurfacesMissingProviderCoverage() {
    MetricCatalogService catalog = mock(MetricCatalogService.class);
    MetricImpactService impact = mock(MetricImpactService.class);
    MetricUsageService usage = mock(MetricUsageService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricLineageImpactProvider> lineageProviders = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricConsumptionTargetProvider> targetProviders = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricObservedUsageProvider> observedProviders = mock(ObjectProvider.class);

    Metric metric = metric();
    when(catalog.get(9L)).thenReturn(metric);
    when(impact.dependencyContext(metric)).thenReturn(
        new MetricImpactService.DependencyContext(List.of(), MetricImpactService.AuthoringNextStep.VALIDATE));

    MetricUsagePO reference = new MetricUsagePO();
    reference.setId(7L);
    reference.setUsageType("DATASET");
    reference.setUsageId(21L);
    reference.setUsageName("sales_dataset");
    reference.setCreateTime(LocalDateTime.of(2026, 9, 27, 12, 0));
    when(usage.listByMetric(9L)).thenReturn(List.of(reference));
    when(lineageProviders.orderedStream()).thenReturn(Stream.empty());
    when(targetProviders.orderedStream()).thenReturn(Stream.empty());
    when(observedProviders.orderedStream()).thenReturn(Stream.empty());

    MetricImpactContextService service = new MetricImpactContextService(
        catalog, impact, usage, lineageProviders, targetProviders, observedProviders);
    MetricImpactContextService.ImpactContext result = service.get(9L);

    assertThat(result.referenceUsage()).hasSize(1);
    assertThat(result.referenceUsage().getFirst().usageType()).isEqualTo("DATASET");
    assertThat(result.lineage()).hasSize(1);
    assertThat(result.lineage().getFirst().status())
        .isEqualTo(MetricLineageImpactProvider.CoverageStatus.UNAVAILABLE);
    assertThat(result.consumptionTargets()).hasSize(1);
    assertThat(result.consumptionTargets().getFirst().status())
        .isEqualTo(MetricConsumptionTargetProvider.TargetStatus.NOT_APPLICABLE);
    assertThat(result.observedUsage()).hasSize(1);
    assertThat(result.observedUsage().getFirst().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.NOT_APPLICABLE);
  }

  @Test
  void providerFailuresStayUnavailableAndDoNotBecomeEmptyEvidence() {
    MetricCatalogService catalog = mock(MetricCatalogService.class);
    MetricImpactService impact = mock(MetricImpactService.class);
    MetricUsageService usage = mock(MetricUsageService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricLineageImpactProvider> lineageProviders = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricConsumptionTargetProvider> targetProviders = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricObservedUsageProvider> observedProviders = mock(ObjectProvider.class);
    MetricLineageImpactProvider lineageProvider = mock(MetricLineageImpactProvider.class);
    MetricConsumptionTargetProvider targetProvider = mock(MetricConsumptionTargetProvider.class);
    MetricObservedUsageProvider observedProvider = mock(MetricObservedUsageProvider.class);

    Metric metric = metric();
    when(catalog.get(9L)).thenReturn(metric);
    when(impact.dependencyContext(metric)).thenReturn(
        new MetricImpactService.DependencyContext(List.of(), MetricImpactService.AuthoringNextStep.VALIDATE));
    when(usage.listByMetric(9L)).thenReturn(List.of());

    when(lineageProvider.providerId()).thenReturn("global-lineage");
    when(lineageProvider.inspect(metric)).thenThrow(new IllegalStateException("lineage offline"));
    when(lineageProviders.orderedStream()).thenReturn(Stream.of(lineageProvider));

    when(targetProvider.providerId()).thenReturn("phase4-target");
    when(targetProvider.resolve(metric)).thenThrow(new IllegalStateException("target offline"));
    when(targetProviders.orderedStream()).thenReturn(Stream.of(targetProvider));

    when(observedProvider.providerId()).thenReturn("phase4-consumption");
    when(observedProvider.observe(metric)).thenThrow(new IllegalStateException("usage offline"));
    when(observedProviders.orderedStream()).thenReturn(Stream.of(observedProvider));

    MetricImpactContextService service = new MetricImpactContextService(
        catalog, impact, usage, lineageProviders, targetProviders, observedProviders);
    MetricImpactContextService.ImpactContext result = service.get(9L);

    assertThat(result.referenceUsage()).isEmpty();
    assertThat(result.lineage().getFirst().status())
        .isEqualTo(MetricLineageImpactProvider.CoverageStatus.UNAVAILABLE);
    assertThat(result.lineage().getFirst().reason()).contains("lineage offline");
    assertThat(result.consumptionTargets().getFirst().status())
        .isEqualTo(MetricConsumptionTargetProvider.TargetStatus.UNAVAILABLE);
    assertThat(result.consumptionTargets().getFirst().reason()).contains("target offline");
    assertThat(result.observedUsage().getFirst().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.UNAVAILABLE);
    assertThat(result.observedUsage().getFirst().reason()).contains("usage offline");
  }

  private static Metric metric() {
    Metric metric = mock(Metric.class);
    when(metric.id()).thenReturn(9L);
    when(metric.metricCode()).thenReturn("gmv");
    when(metric.metricName()).thenReturn("GMV");
    when(metric.version()).thenReturn(3);
    return metric;
  }
}
