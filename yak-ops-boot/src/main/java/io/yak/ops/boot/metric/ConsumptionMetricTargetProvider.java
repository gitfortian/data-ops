package io.yak.ops.boot.metric;

import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService.NavigationResolution;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.impact.MetricConsumptionTargetProvider;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Boot adapter from stable Metric Reference Usage to Phase 4 canonical Consumption targets.
 *
 * <p>Only DATASET reference usage has a frozen realization mapping today. Data Service is not
 * guessed from lineage/name/description. CanonicalProductService remains the authority on whether
 * the referenced Dataset is discoverable and what its ProductKey/href is.
 */
@Component
public class ConsumptionMetricTargetProvider implements MetricConsumptionTargetProvider {

  private final MetricUsageService metricUsageService;
  private final ObjectProvider<CanonicalProductService> canonicalProductService;

  public ConsumptionMetricTargetProvider(
      MetricUsageService metricUsageService,
      ObjectProvider<CanonicalProductService> canonicalProductService) {
    this.metricUsageService = metricUsageService;
    this.canonicalProductService = canonicalProductService;
  }

  @Override
  public String providerId() {
    return "phase4-consumption-target";
  }

  @Override
  public List<TargetResolution> resolve(Metric metric) {
    List<MetricUsagePO> datasetReferences = metricUsageService.listByMetric(metric.id()).stream()
        .filter(usage -> MetricUsageApi.USAGE_TYPE_DATASET.equals(usage.getUsageType()))
        .filter(usage -> usage.getUsageId() != null && usage.getUsageId() > 0)
        .toList();
    if (datasetReferences.isEmpty()) {
      return List.of(TargetResolution.notApplicable(
          providerId(), "No stable governed Dataset realization is recorded in Metric Reference Usage"));
    }

    CanonicalProductService service = canonicalProductService.getIfAvailable();
    List<TargetResolution> results = new ArrayList<>();
    for (MetricUsagePO reference : datasetReferences) {
      ProductKey key = new ProductKey(ProductType.DATASET, String.valueOf(reference.getUsageId()));
      if (service == null) {
        results.add(TargetResolution.unavailable(
            providerId(), reference.getId(), reference.getUsageType(), reference.getUsageId(),
            key.value(), "Phase 4 CanonicalProductService is unavailable"));
        continue;
      }
      results.add(resolveSafely(service, reference, key));
    }
    return List.copyOf(results);
  }

  private TargetResolution resolveSafely(
      CanonicalProductService service, MetricUsagePO reference, ProductKey expectedKey) {
    try {
      NavigationResolution resolution = service.fromSource(
          ProductType.DATASET, String.valueOf(reference.getUsageId()));
      return new TargetResolution(
          providerId(),
          reference.getId(),
          reference.getUsageType(),
          reference.getUsageId(),
          status(resolution.state()),
          resolution.productKey() == null ? expectedKey.value() : resolution.productKey().value(),
          resolution.canonicalHref(),
          resolution.reason(),
          LocalDateTime.now());
    } catch (SecurityException exception) {
      return new TargetResolution(
          providerId(), reference.getId(), reference.getUsageType(), reference.getUsageId(),
          TargetStatus.FORBIDDEN, expectedKey.value(), null, safeReason(exception), LocalDateTime.now());
    } catch (RuntimeException exception) {
      return TargetResolution.unavailable(
          providerId(), reference.getId(), reference.getUsageType(), reference.getUsageId(),
          expectedKey.value(), safeReason(exception));
    }
  }

  private static TargetStatus status(String state) {
    if (state == null || state.isBlank()) return TargetStatus.UNAVAILABLE;
    try {
      return TargetStatus.valueOf(state.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      return TargetStatus.UNAVAILABLE;
    }
  }

  private static String safeReason(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : message;
  }
}
