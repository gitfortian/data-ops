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
  void keepsReferenceUsageSeparateWhenNoObservedProviderExists() {
    MetricCatalogService catalog = mock(MetricCatalogService.class);
    MetricImpactService impact = mock(MetricImpactService.class);
    MetricUsageService usage = mock(MetricUsageService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricObservedUsageProvider> providers = mock(ObjectProvider.class);

    Metric metric = mock(Metric.class);
    when(metric.id()).thenReturn(9L);
    when(metric.metricCode()).thenReturn("gmv");
    when(metric.metricName()).thenReturn("GMV");
    when(metric.version()).thenReturn(3);
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
    when(providers.orderedStream()).thenReturn(Stream.empty());

    MetricImpactContextService service = new MetricImpactContextService(catalog, impact, usage, providers);
    MetricImpactContextService.ImpactContext result = service.get(9L);

    assertThat(result.referenceUsage()).hasSize(1);
    assertThat(result.referenceUsage().getFirst().usageType()).isEqualTo("DATASET");
    assertThat(result.observedUsage()).hasSize(1);
    assertThat(result.observedUsage().getFirst().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.NOT_APPLICABLE);
  }

  @Test
  void providerFailureIsUnavailableRatherThanEmpty() {
    MetricCatalogService catalog = mock(MetricCatalogService.class);
    MetricImpactService impact = mock(MetricImpactService.class);
    MetricUsageService usage = mock(MetricUsageService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricObservedUsageProvider> providers = mock(ObjectProvider.class);
    MetricObservedUsageProvider provider = mock(MetricObservedUsageProvider.class);

    Metric metric = mock(Metric.class);
    when(metric.id()).thenReturn(9L);
    when(metric.metricCode()).thenReturn("gmv");
    when(metric.metricName()).thenReturn("GMV");
    when(metric.version()).thenReturn(3);
    when(catalog.get(9L)).thenReturn(metric);
    when(impact.dependencyContext(metric)).thenReturn(
        new MetricImpactService.DependencyContext(List.of(), MetricImpactService.AuthoringNextStep.VALIDATE));
    when(usage.listByMetric(9L)).thenReturn(List.of());
    when(provider.providerId()).thenReturn("phase4-consumption");
    when(provider.observe(metric)).thenThrow(new IllegalStateException("provider offline"));
    when(providers.orderedStream()).thenReturn(Stream.of(provider));

    MetricImpactContextService service = new MetricImpactContextService(catalog, impact, usage, providers);
    MetricImpactContextService.ImpactContext result = service.get(9L);

    assertThat(result.referenceUsage()).isEmpty();
    assertThat(result.observedUsage()).hasSize(1);
    assertThat(result.observedUsage().getFirst().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.UNAVAILABLE);
    assertThat(result.observedUsage().getFirst().reason()).contains("provider offline");
  }
}
