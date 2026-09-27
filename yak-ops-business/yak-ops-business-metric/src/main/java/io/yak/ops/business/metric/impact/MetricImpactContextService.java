package io.yak.ops.business.metric.impact;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.lineage.MetricLineageRegistrationService;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Phase 5 impact read model.
 *
 * <p>Dependency evidence, Lineage evidence, Reference Usage and Observed Runtime Usage remain
 * distinct evidence classes. This service only composes owning-domain reads; it does not persist a
 * second copy of Lineage or Consumption truth.
 */
@Component
@Slf4j
public class MetricImpactContextService {

  private static final String LINEAGE_PROVIDER = "lineage";
  private static final int DIRECT_LINEAGE_DEPTH = 1;

  private final MetricCatalogService catalogService;
  private final MetricImpactService impactService;
  private final MetricUsageService usageService;
  private final ObjectProvider<MetricObservedUsageProvider> observedUsageProviders;
  private final ObjectProvider<LineageQueryService> lineageQueryService;

  public MetricImpactContextService(
      MetricCatalogService catalogService,
      MetricImpactService impactService,
      MetricUsageService usageService,
      ObjectProvider<MetricObservedUsageProvider> observedUsageProviders,
      ObjectProvider<LineageQueryService> lineageQueryService) {
    this.catalogService = catalogService;
    this.impactService = impactService;
    this.usageService = usageService;
    this.observedUsageProviders = observedUsageProviders;
    this.lineageQueryService = lineageQueryService;
  }

  public ImpactContext get(Long metricId) {
    Metric metric = catalogService.get(metricId);
    MetricImpactService.DependencyContext dependencies = impactService.dependencyContext(metric);
    LineageCoverage lineage = lineageCoverage(metric);

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
        lineage,
        referenceUsage,
        List.copyOf(observedUsage),
        LocalDateTime.now());
  }

  private LineageCoverage lineageCoverage(Metric metric) {
    String rootAssetKey = MetricLineageRegistrationService.metricAssetKey(metric.id());
    LineageQueryService queryService = lineageQueryService.getIfAvailable();
    if (queryService == null) {
      return LineageCoverage.unavailable(
          rootAssetKey, "Lineage query provider is not registered");
    }

    try {
      Optional<LineageAsset> rootOptional = queryService.findAssetByKey(rootAssetKey);
      if (rootOptional.isEmpty()) {
        return LineageCoverage.empty(
            rootAssetKey, null, null,
            "Metric has no registered lineage asset in the current Project");
      }

      LineageAsset root = rootOptional.get();
      LineageGraph upstream = queryService.upstream(root.id(), DIRECT_LINEAGE_DEPTH);
      LineageGraph downstream = queryService.downstream(root.id(), DIRECT_LINEAGE_DEPTH);

      Map<Long, LineageAsset> assets = new LinkedHashMap<>();
      assets.put(root.id(), root);
      addAssets(assets, upstream);
      addAssets(assets, downstream);

      Map<Long, LineageRelation> relations = new LinkedHashMap<>();
      addRelations(relations, upstream);
      addRelations(relations, downstream);

      List<LineageEvidence> evidence = relations.values().stream()
          .map(relation -> toLineageEvidence(relation, root.id(), assets))
          .toList();
      Instant observedAt = evidence.stream()
          .map(LineageEvidence::observedAt)
          .filter(java.util.Objects::nonNull)
          .max(Instant::compareTo)
          .orElse(root.updateTime());

      if (evidence.isEmpty()) {
        return LineageCoverage.empty(
            rootAssetKey, root.id(), observedAt,
            "Lineage asset exists but no direct upstream/downstream relation is recorded");
      }
      return new LineageCoverage(
          LINEAGE_PROVIDER,
          MetricObservedUsageProvider.CoverageStatus.READY,
          root.id(),
          rootAssetKey,
          evidence,
          observedAt,
          "Direct depth=1 lineage only; use the canonical Lineage view for deeper traversal");
    } catch (RuntimeException exception) {
      log.warn("Lineage impact coverage failed: metricId={}, error={}",
          metric.id(), exception.getMessage());
      return LineageCoverage.unavailable(rootAssetKey, exception.getMessage());
    }
  }

  private static void addAssets(Map<Long, LineageAsset> target, LineageGraph graph) {
    if (graph == null || graph.nodes() == null) return;
    for (LineageAsset asset : graph.nodes()) {
      if (asset != null) target.putIfAbsent(asset.id(), asset);
    }
  }

  private static void addRelations(Map<Long, LineageRelation> target, LineageGraph graph) {
    if (graph == null || graph.relations() == null) return;
    for (LineageRelation relation : graph.relations()) {
      if (relation != null) target.putIfAbsent(relation.id(), relation);
    }
  }

  private static LineageEvidence toLineageEvidence(
      LineageRelation relation, long rootAssetId, Map<Long, LineageAsset> assets) {
    LineageAsset source = assets.get(relation.sourceAssetId());
    LineageAsset target = assets.get(relation.targetAssetId());
    String direction = relation.targetAssetId() == rootAssetId
        ? "UPSTREAM"
        : relation.sourceAssetId() == rootAssetId ? "DOWNSTREAM" : "RELATED";
    return new LineageEvidence(
        relation.id(),
        direction,
        relation.relationType() != null ? relation.relationType().name() : null,
        source != null ? source.assetKey() : String.valueOf(relation.sourceAssetId()),
        target != null ? target.assetKey() : String.valueOf(relation.targetAssetId()),
        relation.sourceType(),
        relation.sourceId(),
        relation.version(),
        relation.observedAt() != null ? relation.observedAt() : relation.updateTime());
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
      LineageCoverage lineage,
      List<ReferenceUsage> referenceUsage,
      List<MetricObservedUsageProvider.Coverage> observedUsage,
      LocalDateTime generatedAt) {}

  public record LineageCoverage(
      String provider,
      MetricObservedUsageProvider.CoverageStatus status,
      Long rootAssetId,
      String rootAssetKey,
      List<LineageEvidence> evidence,
      Instant observedAt,
      String reason) {

    public LineageCoverage {
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    static LineageCoverage empty(
        String rootAssetKey, Long rootAssetId, Instant observedAt, String reason) {
      return new LineageCoverage(
          LINEAGE_PROVIDER,
          MetricObservedUsageProvider.CoverageStatus.EMPTY,
          rootAssetId,
          rootAssetKey,
          List.of(),
          observedAt,
          reason);
    }

    static LineageCoverage unavailable(String rootAssetKey, String reason) {
      return new LineageCoverage(
          LINEAGE_PROVIDER,
          MetricObservedUsageProvider.CoverageStatus.UNAVAILABLE,
          null,
          rootAssetKey,
          List.of(),
          null,
          reason == null || reason.isBlank() ? "Lineage provider unavailable" : reason);
    }
  }

  public record LineageEvidence(
      Long relationId,
      String direction,
      String relationType,
      String sourceAssetKey,
      String targetAssetKey,
      String sourceType,
      String sourceId,
      String version,
      Instant observedAt) {}

  public record ReferenceUsage(
      Long referenceId,
      String usageType,
      Long usageId,
      String usageName,
      LocalDateTime recordedAt) {}
}
