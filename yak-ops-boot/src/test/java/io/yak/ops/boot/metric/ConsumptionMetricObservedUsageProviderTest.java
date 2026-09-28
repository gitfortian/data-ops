package io.yak.ops.boot.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.ConsumerType;
import io.yak.ops.business.consumption.relationship.ConsumptionMode;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageOutcome;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricUsage;
import io.yak.ops.business.metric.impact.MetricObservedUsageProvider.CoverageStatus;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ConsumptionMetricObservedUsageProviderTest {

  @Test
  void noDatasetReferenceIsNotApplicable() {
    Fixture fixture = fixture();
    when(fixture.metricUsageService.listByMetric(9L)).thenReturn(List.of());

    assertThat(fixture.provider.observe(metric(9L)).status()).isEqualTo(CoverageStatus.NOT_APPLICABLE);
  }

  @Test
  void datasetReferenceWithoutRuntimeEvidenceIsEmpty() {
    Fixture fixture = fixture();
    when(fixture.metricUsageService.listByMetric(9L)).thenReturn(List.of(datasetUsage(21L)));
    when(fixture.usageEvidenceProvider.getIfAvailable()).thenReturn(fixture.usageEvidenceService);
    when(fixture.currentProject.requireProjectId()).thenReturn(1L);
    when(fixture.usageEvidenceService.list(
        1L, new ProductKey(ProductType.DATASET, "21"), null, 100)).thenReturn(List.of());

    assertThat(fixture.provider.observe(metric(9L)).status()).isEqualTo(CoverageStatus.EMPTY);
  }

  @Test
  void datasetRuntimeEvidenceIsReadyWithStableIdentities() {
    Fixture fixture = fixture();
    when(fixture.metricUsageService.listByMetric(9L)).thenReturn(List.of(datasetUsage(21L)));
    when(fixture.usageEvidenceProvider.getIfAvailable()).thenReturn(fixture.usageEvidenceService);
    when(fixture.currentProject.requireProjectId()).thenReturn(1L);

    ProductKey productKey = new ProductKey(ProductType.DATASET, "21");
    UsageEvidence row = new UsageEvidence(
        77L,
        1L,
        productKey,
        new SourceVersionRef("210", "v3"),
        new ConsumerRef(ConsumerType.USER, "SECURITY", "42", "alice"),
        LocalDateTime.of(2026, 9, 27, 13, 0),
        ConsumptionMode.QUERY,
        UsageOutcome.SUCCESS,
        "dataset-query",
        "query:991",
        "dedupe-991",
        LocalDateTime.of(2026, 9, 27, 13, 1));
    when(fixture.usageEvidenceService.list(1L, productKey, null, 100)).thenReturn(List.of(row));

    var coverage = fixture.provider.observe(metric(9L));
    assertThat(coverage.status()).isEqualTo(CoverageStatus.READY);
    assertThat(coverage.evidence()).hasSize(1);
    assertThat(coverage.evidence().getFirst().evidenceId()).isEqualTo("USAGE_EVIDENCE:77");
    assertThat(coverage.evidence().getFirst().productKey()).isEqualTo("DATASET:21");
    assertThat(coverage.evidence().getFirst().consumerRef()).isEqualTo("USER:SECURITY:42");
    assertThat(coverage.evidence().getFirst().action()).isEqualTo("QUERY");
  }

  @Test
  void missingUsageEvidenceServiceIsUnavailable() {
    Fixture fixture = fixture();
    when(fixture.metricUsageService.listByMetric(9L)).thenReturn(List.of(datasetUsage(21L)));
    when(fixture.usageEvidenceProvider.getIfAvailable()).thenReturn(null);

    assertThat(fixture.provider.observe(metric(9L)).status()).isEqualTo(CoverageStatus.UNAVAILABLE);
  }

  private static Metric metric(Long id) {
    Metric metric = mock(Metric.class);
    when(metric.id()).thenReturn(id);
    return metric;
  }

  private static MetricUsage datasetUsage(Long datasetId) {
    return new MetricUsage(null, 9L, null, "DATASET", datasetId, null, null);
  }

  @SuppressWarnings("unchecked")
  private static Fixture fixture() {
    MetricUsageService metricUsageService = mock(MetricUsageService.class);
    ObjectProvider<UsageEvidenceService> usageEvidenceProvider = mock(ObjectProvider.class);
    UsageEvidenceService usageEvidenceService = mock(UsageEvidenceService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    return new Fixture(
        metricUsageService,
        usageEvidenceProvider,
        usageEvidenceService,
        currentProject,
        new ConsumptionMetricObservedUsageProvider(metricUsageService, usageEvidenceProvider, currentProject));
  }

  private record Fixture(
      MetricUsageService metricUsageService,
      ObjectProvider<UsageEvidenceService> usageEvidenceProvider,
      UsageEvidenceService usageEvidenceService,
      CurrentProject currentProject,
      ConsumptionMetricObservedUsageProvider provider) {}
}
