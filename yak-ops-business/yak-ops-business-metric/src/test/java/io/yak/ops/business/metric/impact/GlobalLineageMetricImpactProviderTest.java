package io.yak.ops.business.metric.impact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.metric.domain.Metric;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GlobalLineageMetricImpactProviderTest {

  @Test
  void missingMetricLineageAssetIsEmptyNotUnavailable() {
    LineageQueryService query = mock(LineageQueryService.class);
    when(query.findAssetByKey("metric:9")).thenReturn(Optional.empty());

    var coverage = new GlobalLineageMetricImpactProvider(query).inspect(metric(9L));

    assertThat(coverage.status()).isEqualTo(MetricLineageImpactProvider.CoverageStatus.EMPTY);
    assertThat(coverage.rootAssetKey()).isEqualTo("metric:9");
  }

  @Test
  void graphEvidencePreservesStableLineageIdentitiesAndTimes() {
    LineageQueryService query = mock(LineageQueryService.class);
    LineageAsset root = asset(90L, "metric:9", LineageAssetType.METRIC, "GMV", "METRIC", "9");
    LineageAsset model = asset(80L, "modeling:model:12", LineageAssetType.TABLE, "dwd_order", "MODELING", "12");
    LineageAsset dataset = asset(100L, "dataset:21", LineageAssetType.DATASET, "sales_dataset", "DATASET", "21");
    LineageRelation upstreamRelation = relation(1L, 80L, 90L, LineageRelationType.CONSUMES);
    LineageRelation downstreamRelation = relation(2L, 90L, 100L, LineageRelationType.DERIVES_FROM);

    when(query.findAssetByKey("metric:9")).thenReturn(Optional.of(root));
    when(query.upstream(90L, 2)).thenReturn(new LineageGraph(
        root, LineageDirection.UPSTREAM, 2, List.of(root, model), List.of(upstreamRelation)));
    when(query.downstream(90L, 2)).thenReturn(new LineageGraph(
        root, LineageDirection.DOWNSTREAM, 2, List.of(root, dataset), List.of(downstreamRelation)));

    var coverage = new GlobalLineageMetricImpactProvider(query).inspect(metric(9L));

    assertThat(coverage.status()).isEqualTo(MetricLineageImpactProvider.CoverageStatus.READY);
    assertThat(coverage.rootAssetId()).isEqualTo(90L);
    assertThat(coverage.upstream()).extracting(MetricLineageImpactProvider.LineageNode::assetKey)
        .containsExactly("modeling:model:12");
    assertThat(coverage.downstream()).extracting(MetricLineageImpactProvider.LineageNode::assetKey)
        .containsExactly("dataset:21");
    assertThat(coverage.relations()).extracting(MetricLineageImpactProvider.LineageEdge::relationId)
        .containsExactly(1L, 2L);
    assertThat(coverage.observedAt()).isNotNull();
  }

  @Test
  void lineageProviderFailureIsUnavailableAndSecurityFailureIsForbidden() {
    LineageQueryService unavailableQuery = mock(LineageQueryService.class);
    when(unavailableQuery.findAssetByKey("metric:9"))
        .thenThrow(new IllegalStateException("lineage store offline"));
    var unavailable = new GlobalLineageMetricImpactProvider(unavailableQuery).inspect(metric(9L));
    assertThat(unavailable.status()).isEqualTo(MetricLineageImpactProvider.CoverageStatus.UNAVAILABLE);
    assertThat(unavailable.reason()).contains("offline");

    LineageQueryService forbiddenQuery = mock(LineageQueryService.class);
    when(forbiddenQuery.findAssetByKey("metric:9"))
        .thenThrow(new SecurityException("forbidden"));
    var forbidden = new GlobalLineageMetricImpactProvider(forbiddenQuery).inspect(metric(9L));
    assertThat(forbidden.status()).isEqualTo(MetricLineageImpactProvider.CoverageStatus.FORBIDDEN);
  }

  private static Metric metric(Long id) {
    Metric metric = mock(Metric.class);
    when(metric.id()).thenReturn(id);
    return metric;
  }

  private static LineageAsset asset(
      long id,
      String key,
      LineageAssetType type,
      String name,
      String sourceType,
      String sourceId) {
    Instant time = Instant.parse("2026-09-27T06:00:00Z");
    return new LineageAsset(
        id, 1L, key, type, name, sourceType, sourceId,
        null, null, null, null, null, null, null, time, time);
  }

  private static LineageRelation relation(
      long id, long sourceId, long targetId, LineageRelationType type) {
    Instant time = Instant.parse("2026-09-27T06:05:00Z");
    return new LineageRelation(
        id, 1L, sourceId, targetId, type,
        "METRIC", "9", null, null, "v3", time, null, time, time);
  }
}
