package io.yak.ops.business.metric.impact;

import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver.ReferenceResolution;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver.ResolutionStatus;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricDependencyRepository;
import io.yak.ops.common.bean.po.metric.MetricDependencyPO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Impact analysis service (T53): user-triggered comparison of
 * metric_dependency.dependency_version against current upstream versions.
 *
 * <p>Upstream current versions are resolved through the same SPI paths as dependency registration.
 * Product-facing dependency health preserves four distinct states: UP_TO_DATE, OUTDATED, REMOVED
 * and UNAVAILABLE. Provider failure is never converted into a fake missing/removed dependency.
 */
@Component
@Slf4j
public class MetricImpactService {

  private static final Set<String> METRIC_DEPENDENCY_TYPES = Set.of("REF_METRIC", "COMPOSITION");

  private final MetricCatalogService catalogService;
  private final MetricDependencyRepository dependencyRepository;
  private final MetricUsageApi usageApi;
  private final MetricReferenceResolver referenceResolver;

  public MetricImpactService(
      MetricCatalogService catalogService,
      MetricDependencyRepository dependencyRepository,
      MetricUsageApi usageApi,
      MetricReferenceResolver referenceResolver) {
    this.catalogService = catalogService;
    this.dependencyRepository = dependencyRepository;
    this.usageApi = usageApi;
    this.referenceResolver = referenceResolver;
  }

  public ImpactReport checkUpstreamChanges(Long metricId) {
    Metric metric = catalogService.get(metricId);
    List<MetricDependencyPO> deps = dependencyRepository.listByMetric(metricId);

    List<Long> metricRefIds = deps.stream()
        .filter(dep -> METRIC_DEPENDENCY_TYPES.contains(dep.getDependencyType()))
        .map(MetricDependencyPO::getDependencyId)
        .filter(java.util.Objects::nonNull)
        .toList();
    Map<Long, Metric> currentMetrics = referenceResolver.metricsById(metricRefIds);

    List<DependencyChange> changes = new ArrayList<>();
    for (MetricDependencyPO dep : deps) {
      DependencyResolution resolution = resolveDependency(dep, currentMetrics);
      changes.add(new DependencyChange(
          dep.getDependencyType(),
          dep.getDependencyId(),
          dep.getDependencyCode(),
          dep.getDependencyVersion(),
          resolution.currentVersion(),
          legacyChangeStatus(resolution.health()),
          resolution.health()));
    }

    MetricUsageApi.UsageSummary usage = usageApi.summary(metricId);
    return new ImpactReport(
        metricId,
        metric.metricCode(),
        metric.metricName(),
        changes,
        usage.totalCount(),
        authoringNextStep(changes));
  }

  private DependencyResolution resolveDependency(
      MetricDependencyPO dep, Map<Long, Metric> currentMetrics) {
    String type = dep.getDependencyType();
    Long depId = dep.getDependencyId();
    Integer registeredVersion = dep.getDependencyVersion();
    if (depId == null) {
      return new DependencyResolution(null, DependencyHealth.UNAVAILABLE);
    }

    if (METRIC_DEPENDENCY_TYPES.contains(type)) {
      Metric upstream = currentMetrics.get(depId);
      if (upstream == null) {
        return new DependencyResolution(null, DependencyHealth.REMOVED);
      }
      return new DependencyResolution(
          upstream.version(), dependencyHealth(registeredVersion, upstream.version()));
    }

    ReferenceResolution resolution = switch (type) {
      case "MODEL" -> referenceResolver.modelReferenceResolution(depId);
      case "CALIBER", "UNIT" -> referenceResolver.standardReferenceResolution(depId);
      default -> ReferenceResolution.unavailable();
    };
    if (resolution.status() == ResolutionStatus.REMOVED) {
      return new DependencyResolution(null, DependencyHealth.REMOVED);
    }
    if (resolution.status() == ResolutionStatus.UNAVAILABLE) {
      return new DependencyResolution(null, DependencyHealth.UNAVAILABLE);
    }
    Integer currentVersion = resolution.reference().version();
    return new DependencyResolution(
        currentVersion, dependencyHealth(registeredVersion, currentVersion));
  }

  private static DependencyHealth dependencyHealth(Integer registered, Integer current) {
    if (registered == null || current == null) return DependencyHealth.UNAVAILABLE;
    return registered.equals(current)
        ? DependencyHealth.UP_TO_DATE
        : DependencyHealth.OUTDATED;
  }

  private static String legacyChangeStatus(DependencyHealth health) {
    return switch (health) {
      case UP_TO_DATE -> "UNCHANGED";
      case OUTDATED -> "CHANGED";
      case REMOVED -> "MISSING";
      case UNAVAILABLE -> "UNKNOWN";
    };
  }

  private static AuthoringNextStep authoringNextStep(List<DependencyChange> changes) {
    if (changes.stream().anyMatch(change -> change.dependencyHealth() == DependencyHealth.REMOVED)) {
      return AuthoringNextStep.RESOLVE_REMOVED_DEPENDENCY;
    }
    if (changes.stream().anyMatch(change -> change.dependencyHealth() == DependencyHealth.UNAVAILABLE)) {
      return AuthoringNextStep.RETRY_DEPENDENCY_PROVIDER;
    }
    if (changes.stream().anyMatch(change -> change.dependencyHealth() == DependencyHealth.OUTDATED)) {
      return AuthoringNextStep.REVIEW_OUTDATED_DEPENDENCY;
    }
    return AuthoringNextStep.VALIDATE;
  }

  /**
   * 反向影响分析:上游(dependencyType+dependencyId)被哪些指标引用。
   * 基于依赖登记快照反查(idx_yak_metric_dep_target),同一指标多类依赖命中时合并为一行。
   * dependencyType 支持 METRIC 别名(合并 REF_METRIC/COMPOSITION 两类命中)。
   */
  public List<AffectedMetric> findAffectedMetrics(String dependencyType, Long dependencyId) {
    if (dependencyType == null || dependencyType.isBlank() || dependencyId == null) {
      return List.of();
    }
    List<String> types = "METRIC".equals(dependencyType)
        ? List.of("REF_METRIC", "COMPOSITION")
        : List.of(dependencyType);
    List<MetricDependencyPO> hits = new ArrayList<>();
    for (String type : types) {
      hits.addAll(dependencyRepository.listByDependency(type, dependencyId));
    }
    if (hits.isEmpty()) {
      return List.of();
    }
    Map<Long, Metric> metrics = referenceResolver.metricsById(hits.stream()
        .map(MetricDependencyPO::getMetricId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet()));
    Map<Long, AffectedMetric> byMetric = new LinkedHashMap<>();
    for (MetricDependencyPO hit : hits) {
      Metric metric = metrics.get(hit.getMetricId());
      if (metric == null) {
        continue;
      }
      byMetric.merge(hit.getMetricId(),
          new AffectedMetric(metric.id(), metric.metricCode(), metric.metricName(),
              metric.metricType().name(), metric.status().name(), metric.owner(),
              hit.getDependencyVersion(), List.of(hit.getDependencyType())),
          (a, b) -> new AffectedMetric(a.metricId(), a.metricCode(), a.metricName(),
              a.metricType(), a.metricStatus(), a.owner(),
              minSnapshot(a.registeredVersion(), b.registeredVersion()),
              Stream.concat(a.dependencyTypes().stream(), b.dependencyTypes().stream())
                  .distinct().sorted().toList()));
    }
    return List.copyOf(byMetric.values());
  }

  private static Integer minSnapshot(Integer a, Integer b) {
    if (a == null) return b;
    if (b == null) return a;
    return Math.min(a, b);
  }

  private record DependencyResolution(Integer currentVersion, DependencyHealth health) {}

  public enum DependencyHealth {
    UP_TO_DATE,
    OUTDATED,
    REMOVED,
    UNAVAILABLE
  }

  public enum AuthoringNextStep {
    VALIDATE,
    REVIEW_OUTDATED_DEPENDENCY,
    RESOLVE_REMOVED_DEPENDENCY,
    RETRY_DEPENDENCY_PROVIDER
  }

  public record ImpactReport(
      Long metricId,
      String metricCode,
      String metricName,
      List<DependencyChange> changes,
      long usageCount,
      AuthoringNextStep authoringNextStep) {}

  public record DependencyChange(
      String dependencyType,
      Long dependencyId,
      String dependencyCode,
      Integer registeredVersion,
      Integer currentVersion,
      String changeStatus,
      DependencyHealth dependencyHealth) {}

  public record AffectedMetric(
      Long metricId, String metricCode, String metricName,
      String metricType, String metricStatus, String owner,
      Integer registeredVersion, List<String> dependencyTypes) {}
}
