package io.yak.ops.boot.metric;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.config.ConditionalOnMetricPersistence;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricUsage;
import io.yak.ops.business.metric.impact.MetricObservedUsageProvider;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.core.project.CurrentProject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Boot-level adapter between Metric reference usage and Phase 4 Consumption usage evidence.
 *
 * <p>The stable realization supported today is a Metric Reference Usage row with usageType=DATASET
 * and usageId=<datasetId>. It maps to the existing Consumption ProductKey DATASET:<datasetId>. No
 * Metric ProductKey is introduced and no UsageEvidence is copied into Metric persistence.
 */
@Component
@ConditionalOnMetricPersistence
public class ConsumptionMetricObservedUsageProvider implements MetricObservedUsageProvider {

  private static final int EVIDENCE_LIMIT_PER_PRODUCT = 100;

  private final MetricUsageService metricUsageService;
  private final ObjectProvider<UsageEvidenceService> usageEvidenceService;
  private final CurrentProject currentProject;

  public ConsumptionMetricObservedUsageProvider(
      MetricUsageService metricUsageService,
      ObjectProvider<UsageEvidenceService> usageEvidenceService,
      CurrentProject currentProject) {
    this.metricUsageService = metricUsageService;
    this.usageEvidenceService = usageEvidenceService;
    this.currentProject = currentProject;
  }

  @Override
  public String providerId() {
    return "phase4-consumption";
  }

  @Override
  public Coverage observe(Metric metric) {
    Set<Long> datasetIds = datasetRealizations(metric.id());
    if (datasetIds.isEmpty()) {
      return Coverage.notApplicable(
          providerId(), "No governed Dataset realization is recorded in Metric Reference Usage");
    }

    UsageEvidenceService service = usageEvidenceService.getIfAvailable();
    if (service == null) {
      return Coverage.unavailable(providerId(), "Phase 4 UsageEvidence service is unavailable");
    }

    Long projectId = currentProject.requireProjectId();
    List<Evidence> evidence = new ArrayList<>();
    for (Long datasetId : datasetIds) {
      ProductKey productKey = new ProductKey(ProductType.DATASET, String.valueOf(datasetId));
      for (UsageEvidence row : service.list(projectId, productKey, null, EVIDENCE_LIMIT_PER_PRODUCT)) {
        evidence.add(toEvidence(row));
      }
    }

    if (evidence.isEmpty()) {
      return new Coverage(
          providerId(),
          CoverageStatus.EMPTY,
          List.of(),
          "Governed Dataset realization exists, but no runtime consumption evidence is recorded");
    }
    return new Coverage(providerId(), CoverageStatus.READY, evidence, null);
  }

  private Set<Long> datasetRealizations(Long metricId) {
    Set<Long> ids = new LinkedHashSet<>();
    for (MetricUsage usage : metricUsageService.listByMetric(metricId)) {
      if (!MetricUsageApi.USAGE_TYPE_DATASET.equals(usage.usageType())) {
        continue;
      }
      Long id = usage.usageId();
      if (id != null && id > 0) {
        ids.add(id);
      }
    }
    return ids;
  }

  private static Evidence toEvidence(UsageEvidence row) {
    return new Evidence(
        row.id() == null ? row.deduplicationId() : "USAGE_EVIDENCE:" + row.id(),
        row.productKey().value(),
        row.consumerRef().identityKey(),
        row.consumptionMode().name(),
        row.outcome().name(),
        row.observedAt());
  }
}
