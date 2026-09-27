package io.yak.ops.business.metric.impact;

import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Phase 5 impact read model.
 *
 * <p>Reference Usage remains Metric-owned declaration truth. Observed Usage remains projected from
 * external owning domains through {@link MetricObservedUsageProvider}. The two are deliberately not
 * merged into a single count or list.
 */
@Component
@Slf4j
public class MetricImpactContextService {

  private final MetricCatalogService catalogService;
  private final MetricImpactService impactService;
  private final MetricUsageService usageService;
  private final ObjectProvider<MetricObservedUsageProvider> observedUsageProviders;

  public MetricImpactContextService(
      MetricCatalogService catalogService,
      MetricImpactService impactService,
      MetricUsageService usageService,
      ObjectProvider<MetricObservedUsageProvider> observedUsageProviders) {
    this.catalogService = catalogService;
    this.impactService = impactService;
    this.usageService = usageService;
    this.observedUsageProviders = observedUsageProviders;
  }

  public ImpactContext get(Long metricId) {
    Metric metric = catalogService.get(metricId);
    MetricImpactService.DependencyContext dependencies = impactService.dependencyContext(metric);

    List<ReferenceUsage> referenceUsage = usageService.listByMetric(metricId).stream()
        .map(MetricImpactContextService::toReferenceUsage)
        .toList();

    List<MetricObservedUsageProvider> providers = observedUsageProviders.orderedStream().toList();
    List<MetricObservedUsageProvider.Coverage> observedUsage = new ArrayList<>();
    if (providers.isEmpty()) {
      observedUsage.add(MetricObservedUsageProvider.Coverage.notApplicable(
          "consumption-runtime",
          "No stable Metric realization-to-consumption provider is registered"));
    } else {
      for (MetricObservedUsageProvider provider : providers) {
        observedUsage.add(observeSafely(provider, metric));
      }
    }

    return new ImpactContext(
        metric.id(),
        metric.metricCode(),
        metric.metricName(),
        metric.version(),
        dependencies.changes(),
        referenceUsage,
        List.copyOf(observedUsage),
        LocalDateTime.now());
  }

  private MetricObservedUsageProvider.Coverage observeSafely(
      MetricObservedUsageProvider provider, Metric metric) {
    String providerId = provider.providerId();
    try {
      MetricObservedUsageProvider.Coverage coverage = provider.observe(metric);
      if (coverage == null) {
        return MetricObservedUsageProvider.Coverage.unavailable(
            providerId, "Provider returned no coverage result");
      }
      return coverage;
    } catch (RuntimeException exception) {
      log.warn("Observed usage provider failed: provider={}, metricId={}, error={}",
          providerId, metric.id(), exception.getMessage());
      return MetricObservedUsageProvider.Coverage.unavailable(providerId, exception.getMessage());
    }
  }

  private static ReferenceUsage toReferenceUsage(MetricUsagePO po) {
    return new ReferenceUsage(
        po.getId(),
        po.getUsageType(),
        po.getUsageId(),
        po.getUsageName(),
        po.getCreateTime());
  }

  public record ImpactContext(
      Long metricId,
      String metricCode,
      String metricName,
      Integer metricVersion,
      List<MetricImpactService.DependencyChange> dependencies,
      List<ReferenceUsage> referenceUsage,
      List<MetricObservedUsageProvider.Coverage> observedUsage,
      LocalDateTime generatedAt) {}

  public record ReferenceUsage(
      Long referenceId,
      String usageType,
      Long usageId,
      String usageName,
      LocalDateTime recordedAt) {}
}
