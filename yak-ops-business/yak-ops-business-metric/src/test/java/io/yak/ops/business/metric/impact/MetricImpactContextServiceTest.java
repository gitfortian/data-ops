package io.yak.ops.business.metric.impact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricImpactContextServiceTest {

  @Test
  void keepsReferenceUsageSeparateWhenNoObservedProviderExists() {
    Fixture fixture = fixture();

    MetricUsagePO reference = new MetricUsagePO();
    reference.setId(7L);
    reference.setUsageType("DATASET");
    reference.setUsageId(21L);
    reference.setUsageName("sales_dataset");
    reference.setCreateTime(LocalDateTime.of(2026, 9, 27, 12, 0));
    when(fixture.usage.listByMetric(9L)).thenReturn(List.of(reference));
    when(fixture.observedProviders.orderedStream()).thenReturn(Stream.empty());
    when(fixture.lineageProvider.getIfAvailable()).thenReturn(null);

    MetricImpactContextService.ImpactContext result = fixture.service().get(9L);

    assertThat(result.referenceUsage()).hasSize(1);
    assertThat(result.referenceUsage().getFirst().usageType()).isEqualTo("DATASET");
    assertThat(result.observedUsage()).hasSize(1);
    assertThat(result.observedUsage().getFirst().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.NOT_APPLICABLE);
    assertThat(result.lineage().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.UNAVAILABLE);
  }

  @Test
  void observedUsageProviderFailureIsUnavailableRatherThanEmpty() {
    Fixture fixture = fixture();
    MetricObservedUsageProvider provider = mock(MetricObservedUsageProvider.class);

    when(fixture.usage.listByMetric(9L)).thenReturn(List.of());
    when(provider.providerId()).thenReturn("phase4-consumption");
    when(provider.observe(fixture.metric)).thenThrow(new IllegalStateException("provider offline"));
    when(fixture.observedProviders.orderedStream()).thenReturn(Stream.of(provider));
    when(fixture.lineageProvider.getIfAvailable()).thenReturn(null);

    MetricImpactContextService.ImpactContext result = fixture.service().get(9L);

    assertThat(result.referenceUsage()).isEmpty();
    assertThat(result.observedUsage()).hasSize(1);
    assertThat(result.observedUsage().getFirst().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.UNAVAILABLE);
    assertThat(result.observedUsage().getFirst().reason()).contains("provider offline");
  }

  @Test
  void lineageKeepsDirectRelationIdentityAndProvenance() {
    Fixture fixture = fixture();
    LineageQueryService lineage = mock(LineageQueryService.class);
    Instant observedAt = Instant.parse("2026-09-27T05:00:00Z");
    LineageAsset root = asset(90L, "metric:9", LineageAssetType.METRIC, "GMV", observedAt);
    LineageAsset upstream = asset(41L, "modeling:model:31", LineageAssetType.TABLE, "sales_dwd", observedAt);
    LineageRelation relation = new LineageRelation(
        301L,
        1L,
        upstream.id(),
        root.id(),
        LineageRelationType.CONSUMES,
        "METRIC",
        "9",
        null,
        null,
        "v3",
        observedAt,
        null,
        observedAt,
        observedAt);

    when(fixture.usage.listByMetric(9L)).thenReturn(List.of());
    when(fixture.observedProviders.orderedStream()).thenReturn(Stream.empty());
    when(fixture.lineageProvider.getIfAvailable()).thenReturn(lineage);
    when(lineage.findAssetByKey("metric:9")).thenReturn(Optional.of(root));
    when(lineage.upstream(root.id(), 1)).thenReturn(new LineageGraph(
        root, LineageDirection.UPSTREAM, 1, List.of(root, upstream), List.of(relation)));
    when(lineage.downstream(root.id(), 1)).thenReturn(new LineageGraph(
        root, LineageDirection.DOWNSTREAM, 1, List.of(root), List.of()));

    MetricImpactContextService.ImpactContext result = fixture.service().get(9L);

    assertThat(result.lineage().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.READY);
    assertThat(result.lineage().rootAssetKey()).isEqualTo("metric:9");
    assertThat(result.lineage().evidence()).hasSize(1);
    assertThat(result.lineage().evidence().getFirst().direction()).isEqualTo("UPSTREAM");
    assertThat(result.lineage().evidence().getFirst().sourceAssetKey()).isEqualTo("modeling:model:31");
    assertThat(result.lineage().evidence().getFirst().targetAssetKey()).isEqualTo("metric:9");
    assertThat(result.lineage().evidence().getFirst().sourceType()).isEqualTo("METRIC");
    assertThat(result.lineage().evidence().getFirst().version()).isEqualTo("v3");
    assertThat(result.lineage().evidence().getFirst().observedAt()).isEqualTo(observedAt);
  }

  @Test
  void missingLineageAssetIsEmptyButProviderFailureIsUnavailable() {
    Fixture fixture = fixture();
    LineageQueryService lineage = mock(LineageQueryService.class);

    when(fixture.usage.listByMetric(9L)).thenReturn(List.of());
    when(fixture.observedProviders.orderedStream()).thenReturn(Stream.empty());
    when(fixture.lineageProvider.getIfAvailable()).thenReturn(lineage);
    when(lineage.findAssetByKey("metric:9")).thenReturn(Optional.empty());

    MetricImpactContextService.ImpactContext empty = fixture.service().get(9L);
    assertThat(empty.lineage().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.EMPTY);
    assertThat(empty.lineage().rootAssetKey()).isEqualTo("metric:9");

    when(lineage.findAssetByKey("metric:9"))
        .thenThrow(new IllegalStateException("lineage offline"));
    MetricImpactContextService.ImpactContext unavailable = fixture.service().get(9L);
    assertThat(unavailable.lineage().status())
        .isEqualTo(MetricObservedUsageProvider.CoverageStatus.UNAVAILABLE);
    assertThat(unavailable.lineage().reason()).contains("lineage offline");
  }

  private static LineageAsset asset(
      long id, String key, LineageAssetType type, String name, Instant timestamp) {
    return new LineageAsset(
        id,
        1L,
        key,
        type,
        name,
        "TEST",
        String.valueOf(id),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        timestamp,
        timestamp);
  }

  private static Fixture fixture() {
    MetricCatalogService catalog = mock(MetricCatalogService.class);
    MetricImpactService impact = mock(MetricImpactService.class);
    MetricUsageService usage = mock(MetricUsageService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MetricObservedUsageProvider> observedProviders = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<LineageQueryService> lineageProvider = mock(ObjectProvider.class);

    Metric metric = mock(Metric.class);
    when(metric.id()).thenReturn(9L);
    when(metric.metricCode()).thenReturn("gmv");
    when(metric.metricName()).thenReturn("GMV");
    when(metric.version()).thenReturn(3);
    when(catalog.get(9L)).thenReturn(metric);
    when(impact.dependencyContext(metric)).thenReturn(
        new MetricImpactService.DependencyContext(
            List.of(), MetricImpactService.AuthoringNextStep.VALIDATE));

    return new Fixture(catalog, impact, usage, observedProviders, lineageProvider, metric);
  }

  private record Fixture(
      MetricCatalogService catalog,
      MetricImpactService impact,
      MetricUsageService usage,
      ObjectProvider<MetricObservedUsageProvider> observedProviders,
      ObjectProvider<LineageQueryService> lineageProvider,
      Metric metric) {
    MetricImpactContextService service() {
      return new MetricImpactContextService(
          catalog, impact, usage, observedProviders, lineageProvider);
    }
  }
}
