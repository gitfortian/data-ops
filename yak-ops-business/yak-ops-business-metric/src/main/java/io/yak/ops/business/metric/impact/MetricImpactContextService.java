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
 * Phase 5 canonical impact read model.
 *
 * <p>Dependency, Lineage, Reference Usage, governed Consumption targets and Observed Runtime Usage
 * remain separate evidence classes with their original owning providers and coverage semantics.
 * Nothing is collapsed into a synthetic "impact count" or copied into Metric persistence.
 */
@Component
@Slf4j
public class MetricImpactContextService {

  private final MetricCatalogService catalogService;
  private final MetricImpactService impactService;
  private final MetricUsageService usageService;
  private final ObjectProvider<MetricLineageImpactProvider> lineageProviders;
  private final ObjectProvider<MetricConsumptionTargetProvider> consumptionTargetProviders;
  private final ObjectProvider<MetricObservedUsageProvider> observedUsageProviders;

  public MetricImpactContextService(
      MetricCatalogService catalogService,
      MetricImpactService impactService,
      MetricUsageService usageService,
      ObjectProvider<MetricLineageImpactProvider> lineageProviders,
      ObjectProvider<MetricConsumptionTargetProvider> consumptionTargetProviders,
      ObjectProvider<MetricObservedUsageProvider> observedUsageProviders) {
    this.catalogService = catalogService;
    this.impactService = impactService;
    this.usageService = usageService;
    this.lineageProviders = lineageProviders;
    this.consumptionTargetProviders = consumptionTargetProviders;
    this.observedUsageProviders = observedUsageProviders;
  }

  public ImpactContext get(Long metricId) {
    Metric metric = catalogService.get(metricId);
    MetricImpactService.DependencyContext dependencies = impactService.dependencyContext(metric);

    List<ReferenceUsage> referenceUsage = usageService.listByMetric(metricId).stream()
        .map(MetricImpactContextService::toReferenceUsage)
        .toList();

    List<MetricLineageImpactProvider.LineageCoverage> lineage = lineage(metric);
    List<MetricConsumptionTargetProvider.TargetResolution> consumptionTargets =
        consumptionTargets(metric);
    List<MetricObservedUsageProvider.Coverage> observedUsage = observedUsage(metric);

    return new ImpactContext(
        metric.id(),
        metric.metricCode(),
        metric.metricName(),
        metric.version(),
        dependencies.changes(),
        lineage,
        referenceUsage,
        consumptionTargets,
        observedUsage,
        LocalDateTime.now());
  }

  private List<MetricLineageImpactProvider.LineageCoverage> lineage(Metric metric) {
    List<MetricLineageImpactProvider> providers = lineageProviders.orderedStream().toList();
    if (providers.isEmpty()) {
      return List.of(MetricLineageImpactProvider.LineageCoverage.unavailable(
          "global-lineage",
          "metric:" + metric.id(),
          "No Lineage impact provider is registered"));
    }
    List<MetricLineageImpactProvider.LineageCoverage> result = new ArrayList<>();
    for (MetricLineageImpactProvider provider : providers) {
      try {
        MetricLineageImpactProvider.LineageCoverage coverage = provider.inspect(metric);
        result.add(coverage == null
            ? MetricLineageImpactProvider.LineageCoverage.unavailable(
                provider.providerId(), "metric:" + metric.id(), "Provider returned no coverage")
            : coverage);
      } catch (RuntimeException exception) {
        log.warn("Lineage impact provider failed: provider={}, metricId={}, error={}",
            provider.providerId(), metric.id(), exception.getMessage());
        result.add(MetricLineageImpactProvider.LineageCoverage.unavailable(
            provider.providerId(), "metric:" + metric.id(), safeReason(exception)));
      }
    }
    return List.copyOf(result);
  }

  private List<MetricConsumptionTargetProvider.TargetResolution> consumptionTargets(Metric metric) {
    List<MetricConsumptionTargetProvider> providers =
        consumptionTargetProviders.orderedStream().toList();
    if (providers.isEmpty()) {
      return List.of(MetricConsumptionTargetProvider.TargetResolution.notApplicable(
          "consumption-target",
          "No stable Metric realization-to-Consumption target provider is registered"));
    }
    List<MetricConsumptionTargetProvider.TargetResolution> result = new ArrayList<>();
    for (MetricConsumptionTargetProvider provider : providers) {
      try {
        List<MetricConsumptionTargetProvider.TargetResolution> resolved = provider.resolve(metric);
        if (resolved == null || resolved.isEmpty()) {
          result.add(MetricConsumptionTargetProvider.TargetResolution.unavailable(
              provider.providerId(), null, null, null, null,
              "Provider returned no target resolution"));
        } else {
          result.addAll(resolved);
        }
      } catch (RuntimeException exception) {
        log.warn("Consumption target provider failed: provider={}, metricId={}, error={}",
            provider.providerId(), metric.id(), exception.getMessage());
        result.add(MetricConsumptionTargetProvider.TargetResolution.unavailable(
            provider.providerId(), null, null, null, null, safeReason(exception)));
      }
    }
    return List.copyOf(result);
  }

  private List<MetricObservedUsageProvider.Coverage> observedUsage(Metric metric) {
    List<MetricObservedUsageProvider> providers = observedUsageProviders.orderedStream().toList();
    List<MetricObservedUsageProvider.Coverage> result = new ArrayList<>();
    if (providers.isEmpty()) {
      result.add(MetricObservedUsageProvider.Coverage.notApplicable(
          "consumption-runtime",
          "No stable Metric realization-to-consumption provider is registered"));
    } else {
      for (MetricObservedUsageProvider provider : providers) {
        result.add(observeSafely(provider, metric));
      }
    }
    return List.copyOf(result);
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
      return MetricObservedUsageProvider.Coverage.unavailable(providerId, safeReason(exception));
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

  private static String safeReason(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : message;
  }

  public record ImpactContext(
      Long metricId,
      String metricCode,
      String metricName,
      Integer metricVersion,
      List<MetricImpactService.DependencyChange> dependencies,
      List<MetricLineageImpactProvider.LineageCoverage> lineage,
      List<ReferenceUsage> referenceUsage,
      List<MetricConsumptionTargetProvider.TargetResolution> consumptionTargets,
      List<MetricObservedUsageProvider.Coverage> observedUsage,
      LocalDateTime generatedAt) {}

  public record ReferenceUsage(
      Long referenceId,
      String usageType,
      Long usageId,
      String usageName,
      LocalDateTime recordedAt) {}
}
