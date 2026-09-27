package io.yak.ops.boot.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService.NavigationResolution;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.impact.MetricConsumptionTargetProvider.TargetStatus;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ConsumptionMetricTargetProviderTest {

  @Test
  void noDatasetReferenceIsNotApplicableWithoutGuessingTarget() {
    Fixture fixture = fixture();
    when(fixture.metricUsageService.listByMetric(9L)).thenReturn(List.of());

    var result = fixture.provider.resolve(metric(9L));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().status()).isEqualTo(TargetStatus.NOT_APPLICABLE);
    assertThat(result.getFirst().productKey()).isNull();
  }

  @Test
  void missingCanonicalProductServiceIsUnavailableButKeepsStableProductIdentity() {
    Fixture fixture = fixture();
    when(fixture.metricUsageService.listByMetric(9L)).thenReturn(List.of(datasetUsage(7L, 21L)));
    when(fixture.canonicalProvider.getIfAvailable()).thenReturn(null);

    var result = fixture.provider.resolve(metric(9L)).getFirst();

    assertThat(result.status()).isEqualTo(TargetStatus.UNAVAILABLE);
    assertThat(result.referenceId()).isEqualTo(7L);
    assertThat(result.productKey()).isEqualTo("DATASET:21");
    assertThat(result.canonicalHref()).isNull();
  }

  @Test
  void foundDatasetReturnsCanonicalPhase4Handoff() {
    Fixture fixture = fixture();
    when(fixture.metricUsageService.listByMetric(9L)).thenReturn(List.of(datasetUsage(7L, 21L)));
    when(fixture.canonicalProvider.getIfAvailable()).thenReturn(fixture.canonicalService);
    ProductKey key = new ProductKey(ProductType.DATASET, "21");
    when(fixture.canonicalService.fromSource(ProductType.DATASET, "21"))
        .thenReturn(new NavigationResolution(
            "FOUND", key, "/data-analysis/consumption/DATASET%3A21", null));

    var result = fixture.provider.resolve(metric(9L)).getFirst();

    assertThat(result.status()).isEqualTo(TargetStatus.FOUND);
    assertThat(result.productKey()).isEqualTo("DATASET:21");
    assertThat(result.canonicalHref()).isEqualTo("/data-analysis/consumption/DATASET%3A21");
  }

  @Test
  void canonicalForbiddenAndNotFoundRemainDistinct() {
    Fixture forbidden = fixture();
    when(forbidden.metricUsageService.listByMetric(9L)).thenReturn(List.of(datasetUsage(7L, 21L)));
    when(forbidden.canonicalProvider.getIfAvailable()).thenReturn(forbidden.canonicalService);
    ProductKey key = new ProductKey(ProductType.DATASET, "21");
    when(forbidden.canonicalService.fromSource(ProductType.DATASET, "21"))
        .thenReturn(new NavigationResolution("FORBIDDEN", key, null, "permission denied"));

    assertThat(forbidden.provider.resolve(metric(9L)).getFirst().status())
        .isEqualTo(TargetStatus.FORBIDDEN);

    Fixture missing = fixture();
    when(missing.metricUsageService.listByMetric(9L)).thenReturn(List.of(datasetUsage(8L, 22L)));
    when(missing.canonicalProvider.getIfAvailable()).thenReturn(missing.canonicalService);
    ProductKey missingKey = new ProductKey(ProductType.DATASET, "22");
    when(missing.canonicalService.fromSource(ProductType.DATASET, "22"))
        .thenReturn(new NavigationResolution("NOT_FOUND", missingKey, null, "dataset missing"));

    assertThat(missing.provider.resolve(metric(9L)).getFirst().status())
        .isEqualTo(TargetStatus.NOT_FOUND);
  }

  private static Metric metric(Long id) {
    Metric metric = mock(Metric.class);
    when(metric.id()).thenReturn(id);
    return metric;
  }

  private static MetricUsagePO datasetUsage(Long referenceId, Long datasetId) {
    MetricUsagePO usage = new MetricUsagePO();
    usage.setId(referenceId);
    usage.setUsageType("DATASET");
    usage.setUsageId(datasetId);
    usage.setUsageName("dataset-" + datasetId);
    return usage;
  }

  @SuppressWarnings("unchecked")
  private static Fixture fixture() {
    MetricUsageService metricUsageService = mock(MetricUsageService.class);
    ObjectProvider<CanonicalProductService> canonicalProvider = mock(ObjectProvider.class);
    CanonicalProductService canonicalService = mock(CanonicalProductService.class);
    return new Fixture(
        metricUsageService,
        canonicalProvider,
        canonicalService,
        new ConsumptionMetricTargetProvider(metricUsageService, canonicalProvider));
  }

  private record Fixture(
      MetricUsageService metricUsageService,
      ObjectProvider<CanonicalProductService> canonicalProvider,
      CanonicalProductService canonicalService,
      ConsumptionMetricTargetProvider provider) {}
}
