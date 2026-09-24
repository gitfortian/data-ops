package io.yak.ops.business.metric.impact;

import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver;
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
 * <p>Upstream current versions are resolved through the same SPI paths as
 * dependency registration: metrics in-module, models via ModelQueryApi,
 * caliber/unit via StandardQueryApi. Unresolvable upstreams surface as
 * UNKNOWN (无法获取当前版本) instead of a fake "pending" state.
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
      Integer currentVersion = resolveCurrentVersion(dep, currentMetrics);
      boolean removed = isRemoved(dep, currentMetrics);
      changes.add(new DependencyChange(
          dep.getDependencyType(),
          dep.getDependencyId(),
          dep.getDependencyCode(),
          dep.getDependencyVersion(),
          currentVersion,
          changeStatus(dep.getDependencyVersion(), currentVersion, removed),
          dependencyHealth(dep.getDependencyVersion(), currentVersion, removed)));
    }

    MetricUsageApi.UsageSummary usage = usageApi.summary(metricId);
    return new ImpactReport(metricId, metric.metricCode(), metric.metricName(),
        changes, usage.totalCount());
  }

  /** 上游当前版本;解析不到(SPI 缺失/字段为空)时返回 null。 */
  private Integer resolveCurrentVersion(MetricDependencyPO dep, Map<Long, Metric> currentMetrics) {
    String type = dep.getDependencyType();
    Long depId = dep.getDependencyId();
    if (depId == null) {
      return null;
    }
    if (METRIC_DEPENDENCY_TYPES.contains(type)) {
      Metric upstream = currentMetrics.get(depId);
      return upstream == null ? null : upstream.version();
    }
    MetricReferenceResolver.Reference reference = "MODEL".equals(type)
        ? referenceResolver.modelReference(depId)
        : "CALIBER".equals(type) || "UNIT".equals(type)
            ? referenceResolver.standardReference(depId)
            : MetricReferenceResolver.Reference.EMPTY;
    return reference.code() == null ? null : reference.version();
  }

  /** 指标类上游已从目录消失 → 缺失;其他类型 SPI 解析失败按 UNKNOWN 处理而非缺失。 */
  private boolean isRemoved(MetricDependencyPO dep, Map<Long, Metric> currentMetrics) {
    return dep.getDependencyId() != null
        && METRIC_DEPENDENCY_TYPES.contains(dep.getDependencyType())
        && !currentMetrics.containsKey(dep.getDependencyId());
  }

  private static String changeStatus(Integer registered, Integer current, boolean removed) {
    if (removed) return "MISSING";
    if (current == null) return "UNKNOWN";
    if (registered == null) return "UNKNOWN";
    return registered.equals(current) ? "UNCHANGED" : "CHANGED";
  }

  /**
   * Stable product-facing dependency health semantics used by the canonical Metric context.
   *
   * <p>This intentionally coexists with the legacy {@code changeStatus} field so existing
   * API consumers are not broken while Phase 5 adopts explicit product states. A missing
   * snapshot/current version is evidence unavailability, never an empty or healthy result.
   */
  private static DependencyHealth dependencyHealth(
      Integer registered, Integer current, boolean removed) {
    if (removed) return DependencyHealth.REMOVED;
    if (registered == null || current == null) return DependencyHealth.UNAVAILABLE;
    return registered.equals(current)
        ? DependencyHealth.UP_TO_DATE
        : DependencyHealth.OUTDATED;
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

  public enum DependencyHealth {
    UP_TO_DATE,
    OUTDATED,
    REMOVED,
    UNAVAILABLE
  }

  public record ImpactReport(
      Long metricId, String metricCode, String metricName,
      List<DependencyChange> changes, long usageCount) {}

  public record DependencyChange(
      String dependencyType, Long dependencyId, String dependencyCode,
      Integer registeredVersion, Integer currentVersion,
      String changeStatus, DependencyHealth dependencyHealth) {}

  public record AffectedMetric(
      Long metricId, String metricCode, String metricName,
      String metricType, String metricStatus, String owner,
      Integer registeredVersion, List<String> dependencyTypes) {}
}
