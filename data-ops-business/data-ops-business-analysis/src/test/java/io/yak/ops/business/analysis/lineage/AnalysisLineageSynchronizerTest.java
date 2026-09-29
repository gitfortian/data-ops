package io.yak.ops.business.analysis.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.Asset;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.AssetSpec;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.RelationSpec;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.RelationType;
import io.yak.ops.business.analysis.query.AnalysisAggregation;
import io.yak.ops.business.analysis.query.AnalysisFilterBinding;
import io.yak.ops.business.analysis.query.AnalysisFilterOperator;
import io.yak.ops.business.analysis.query.AnalysisMetricBinding;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.query.AnalysisSortBinding;
import io.yak.ops.business.analysis.query.AnalysisSortDirection;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalysisLineageSynchronizerTest {

  @Test
  void syncBuildsStableDatasetAndFieldConsumptionAndMergesUsageRoles() {
    AnalysisLineageGraphGateway lineage = mock(AnalysisLineageGraphGateway.class);
    AtomicLong ids = new AtomicLong(100L);
    when(lineage.requireAssetByKey(anyString()))
        .thenThrow(new IllegalArgumentException("missing"));
    when(lineage.registerAsset(any())).thenAnswer(invocation -> {
      AssetSpec spec = invocation.getArgument(0);
      return new Asset(ids.incrementAndGet(), spec.assetKey());
    });
    AnalysisLineageSynchronizer synchronizer = new AnalysisLineageSynchronizer(
        lineage, new AnalysisFieldUsageExtractor());

    synchronizer.syncCurrent(asset());

    verify(lineage).clearRelationsByEvidence(
        AnalysisLineageSynchronizer.EVIDENCE_SOURCE_TYPE, "7");
    ArgumentCaptor<AssetSpec> assets = ArgumentCaptor.forClass(AssetSpec.class);
    verify(lineage, org.mockito.Mockito.atLeast(4)).registerAsset(assets.capture());
    assertThat(assets.getAllValues()).anyMatch(spec ->
        "chart:analysis:7".equals(spec.assetKey()));
    assertThat(assets.getAllValues()).anyMatch(spec ->
        "dataset:21".equals(spec.assetKey()));
    assertThat(assets.getAllValues()).anyMatch(spec ->
        "dataset-field:21:region".equals(spec.assetKey()));

    ArgumentCaptor<RelationSpec> relations = ArgumentCaptor.forClass(RelationSpec.class);
    verify(lineage, org.mockito.Mockito.times(3)).registerRelation(relations.capture());
    assertThat(relations.getAllValues()).allMatch(relation ->
        relation.relationType() == RelationType.CONSUMES);

    RelationSpec region = relations.getAllValues().stream()
        .filter(relation -> "region".equals(relation.properties().get("fieldId")))
        .findFirst()
        .orElseThrow();
    assertThat(region.properties().get("usageRoles"))
        .isEqualTo(List.of("DIMENSION", "FILTER"));

    RelationSpec amount = relations.getAllValues().stream()
        .filter(relation -> "amount".equals(relation.properties().get("fieldId")))
        .findFirst()
        .orElseThrow();
    assertThat(amount.properties().get("metricAggregations"))
        .isEqualTo(List.of("SUM"));
  }

  @Test
  void clearOnlyRemovesAnalysisScopedEvidence() {
    AnalysisLineageGraphGateway lineage = mock(AnalysisLineageGraphGateway.class);
    AnalysisLineageSynchronizer synchronizer = new AnalysisLineageSynchronizer(
        lineage, new AnalysisFieldUsageExtractor());

    synchronizer.clear(19L);

    verify(lineage).clearRelationsByEvidence(
        AnalysisLineageSynchronizer.EVIDENCE_SOURCE_TYPE, "19");
  }

  private static AnalysisAsset asset() {
    return new AnalysisAsset(
        7L,
        "区域销售",
        "销售分析",
        21L,
        AnalysisChartType.BAR,
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(new AnalysisFilterBinding("region", AnalysisFilterOperator.EQ, "华南")),
            List.of(new AnalysisSortBinding(
                "amount", AnalysisAggregation.SUM, AnalysisSortDirection.DESC)),
            500,
            30),
        new AnalysisVisualConfig(false, false, false, true),
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
