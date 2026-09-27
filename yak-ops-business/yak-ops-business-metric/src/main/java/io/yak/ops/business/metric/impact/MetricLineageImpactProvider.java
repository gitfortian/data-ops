package io.yak.ops.business.metric.impact;

import io.yak.ops.business.metric.domain.Metric;
import java.time.Instant;
import java.util.List;

/** Read-side projection of Lineage-owned evidence into Metric impact context. */
public interface MetricLineageImpactProvider {

  String providerId();

  LineageCoverage inspect(Metric metric);

  enum CoverageStatus {
    READY,
    EMPTY,
    UNAVAILABLE,
    FORBIDDEN,
    NOT_APPLICABLE
  }

  record LineageCoverage(
      String provider,
      CoverageStatus status,
      String rootAssetKey,
      Long rootAssetId,
      List<LineageNode> upstream,
      List<LineageNode> downstream,
      List<LineageEdge> relations,
      Instant observedAt,
      String reason) {

    public LineageCoverage {
      upstream = upstream == null ? List.of() : List.copyOf(upstream);
      downstream = downstream == null ? List.of() : List.copyOf(downstream);
      relations = relations == null ? List.of() : List.copyOf(relations);
    }

    public static LineageCoverage empty(String provider, String rootAssetKey, String reason) {
      return new LineageCoverage(
          provider, CoverageStatus.EMPTY, rootAssetKey, null,
          List.of(), List.of(), List.of(), null, reason);
    }

    public static LineageCoverage unavailable(String provider, String rootAssetKey, String reason) {
      return new LineageCoverage(
          provider, CoverageStatus.UNAVAILABLE, rootAssetKey, null,
          List.of(), List.of(), List.of(), null, reason);
    }
  }

  record LineageNode(
      Long assetId,
      String assetKey,
      String assetType,
      String name,
      String sourceType,
      String sourceId,
      Instant observedAt) {}

  record LineageEdge(
      Long relationId,
      Long sourceAssetId,
      Long targetAssetId,
      String relationType,
      String sourceType,
      String sourceId,
      String version,
      Instant observedAt) {}
}
