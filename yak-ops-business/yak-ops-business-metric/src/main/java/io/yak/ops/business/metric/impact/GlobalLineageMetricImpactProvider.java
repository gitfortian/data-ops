package io.yak.ops.business.metric.impact;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.lineage.MetricLineageRegistrationService;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Global Lineage-owned evidence projection for the Metric impact view. */
@Component
public class GlobalLineageMetricImpactProvider implements MetricLineageImpactProvider {

  private static final int IMPACT_DEPTH = 2;
  private final LineageQueryService queryService;

  public GlobalLineageMetricImpactProvider(LineageQueryService queryService) {
    this.queryService = queryService;
  }

  @Override
  public String providerId() {
    return "global-lineage";
  }

  @Override
  public LineageCoverage inspect(Metric metric) {
    String rootKey = MetricLineageRegistrationService.metricAssetKey(metric.id());
    try {
      LineageAsset root = queryService.findAssetByKey(rootKey).orElse(null);
      if (root == null) {
        return LineageCoverage.empty(
            providerId(), rootKey, "Metric has no registered Lineage asset in the current project");
      }

      LineageGraph upstreamGraph = queryService.upstream(root.id(), IMPACT_DEPTH);
      LineageGraph downstreamGraph = queryService.downstream(root.id(), IMPACT_DEPTH);
      List<LineageNode> upstream = nodesExceptRoot(upstreamGraph, root.id());
      List<LineageNode> downstream = nodesExceptRoot(downstreamGraph, root.id());
      List<LineageEdge> relations = mergeRelations(upstreamGraph, downstreamGraph);

      Instant observedAt = latestObservedAt(root, upstreamGraph, downstreamGraph);
      return new LineageCoverage(
          providerId(),
          CoverageStatus.READY,
          rootKey,
          root.id(),
          upstream,
          downstream,
          relations,
          observedAt,
          upstream.isEmpty() && downstream.isEmpty()
              ? "Lineage asset is registered but has no connected evidence in depth " + IMPACT_DEPTH
              : null);
    } catch (SecurityException exception) {
      return new LineageCoverage(
          providerId(), CoverageStatus.FORBIDDEN, rootKey, null,
          List.of(), List.of(), List.of(), null, safeReason(exception));
    } catch (RuntimeException exception) {
      return LineageCoverage.unavailable(providerId(), rootKey, safeReason(exception));
    }
  }

  private static List<LineageNode> nodesExceptRoot(LineageGraph graph, long rootId) {
    if (graph == null || graph.nodes() == null) return List.of();
    return graph.nodes().stream()
        .filter(node -> node.id() != rootId)
        .map(GlobalLineageMetricImpactProvider::node)
        .toList();
  }

  private static LineageNode node(LineageAsset asset) {
    return new LineageNode(
        asset.id(),
        asset.assetKey(),
        asset.assetType() == null ? null : asset.assetType().name(),
        asset.name(),
        asset.sourceType(),
        asset.sourceId(),
        asset.updateTime() == null ? asset.createTime() : asset.updateTime());
  }

  private static List<LineageEdge> mergeRelations(LineageGraph... graphs) {
    Map<Long, LineageRelation> byId = new LinkedHashMap<>();
    for (LineageGraph graph : graphs) {
      if (graph == null || graph.relations() == null) continue;
      for (LineageRelation relation : graph.relations()) {
        byId.putIfAbsent(relation.id(), relation);
      }
    }
    return byId.values().stream()
        .map(GlobalLineageMetricImpactProvider::edge)
        .toList();
  }

  private static LineageEdge edge(LineageRelation relation) {
    return new LineageEdge(
        relation.id(),
        relation.sourceAssetId(),
        relation.targetAssetId(),
        relation.relationType() == null ? null : relation.relationType().name(),
        relation.sourceType(),
        relation.sourceId(),
        relation.version(),
        relation.observedAt() != null
            ? relation.observedAt()
            : relation.updateTime() != null ? relation.updateTime() : relation.createTime());
  }

  private static Instant latestObservedAt(
      LineageAsset root, LineageGraph upstream, LineageGraph downstream) {
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(root.updateTime(), root.createTime()),
            java.util.stream.Stream.of(upstream, downstream)
                .filter(java.util.Objects::nonNull)
                .flatMap(graph -> java.util.stream.Stream.concat(
                    graph.nodes() == null
                        ? java.util.stream.Stream.empty()
                        : graph.nodes().stream().flatMap(node ->
                            java.util.stream.Stream.of(node.updateTime(), node.createTime())),
                    graph.relations() == null
                        ? java.util.stream.Stream.empty()
                        : graph.relations().stream().flatMap(relation ->
                            java.util.stream.Stream.of(
                                relation.observedAt(), relation.updateTime(), relation.createTime())))))
        .filter(java.util.Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private static String safeReason(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : message;
  }
}
