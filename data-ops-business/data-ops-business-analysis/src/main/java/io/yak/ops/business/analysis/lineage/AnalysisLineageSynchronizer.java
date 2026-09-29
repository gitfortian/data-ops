package io.yak.ops.business.analysis.lineage;

import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.Asset;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.AssetSpec;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.AssetType;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.RelationSpec;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway.RelationType;
import io.yak.ops.business.analysis.lineage.AnalysisFieldUsageExtractor.FieldUsage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Converges the derived Dataset/DatasetField -> reusable Analysis Chart lineage projection. */
@Component
public class AnalysisLineageSynchronizer {

  public static final String EVIDENCE_SOURCE_TYPE = "ANALYSIS_BINDING";

  private final AnalysisLineageGraphGateway lineage;
  private final AnalysisFieldUsageExtractor fieldUsages;

  public AnalysisLineageSynchronizer(
      AnalysisLineageGraphGateway lineage,
      AnalysisFieldUsageExtractor fieldUsages) {
    this.lineage = lineage;
    this.fieldUsages = fieldUsages;
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void syncCurrent(AnalysisAsset analysis) {
    if (analysis == null || analysis.id() <= 0L) return;

    String evidenceId = String.valueOf(analysis.id());
    lineage.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);

    Asset chart = registerChartAsset(analysis);
    Asset dataset = resolveDatasetAsset(analysis.datasetId());
    Instant observedAt = analysis.updateTime() == null ? Instant.now() : analysis.updateTime();

    lineage.registerRelation(new RelationSpec(
        dataset.id(),
        chart.id(),
        RelationType.CONSUMES,
        EVIDENCE_SOURCE_TYPE,
        evidenceId,
        null,
        BigDecimal.ONE,
        "analysis-current:dataset",
        observedAt,
        datasetRelationProperties(analysis)));

    int index = 0;
    for (Map.Entry<String, FieldUsage> entry : fieldUsages.extract(analysis.querySpec()).entrySet()) {
      index++;
      String fieldId = entry.getKey();
      Asset field = resolveDatasetFieldAsset(dataset, analysis.datasetId(), fieldId);
      lineage.registerRelation(new RelationSpec(
          field.id(),
          chart.id(),
          RelationType.CONSUMES,
          EVIDENCE_SOURCE_TYPE,
          evidenceId,
          null,
          BigDecimal.ONE,
          "analysis-current:field:" + index,
          observedAt,
          fieldRelationProperties(analysis, fieldId, entry.getValue())));
    }
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void clear(long analysisId) {
    if (analysisId <= 0L) return;
    lineage.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, String.valueOf(analysisId));
  }

  private Asset registerChartAsset(AnalysisAsset analysis) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("analysisId", String.valueOf(analysis.id()));
    properties.put("datasetId", String.valueOf(analysis.datasetId()));
    properties.put("chartType", analysis.chartType().name());
    if (analysis.description() != null) properties.put("description", analysis.description());
    if (analysis.updateTime() != null) {
      properties.put("analysisUpdatedAt", analysis.updateTime().toString());
    }
    properties.put("bindingMode", "REUSABLE_ANALYSIS");

    return lineage.registerAsset(new AssetSpec(
        chartAssetKey(analysis.id()),
        AssetType.CHART,
        analysis.name(),
        "ANALYSIS",
        String.valueOf(analysis.id()),
        null,
        null,
        null,
        null,
        null,
        null,
        properties));
  }

  private Asset resolveDatasetAsset(long datasetId) {
    String key = datasetAssetKey(datasetId);
    try {
      return lineage.requireAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("lineageRegistration", "ANALYSIS_FALLBACK");
      return lineage.registerAsset(new AssetSpec(
          key,
          AssetType.DATASET,
          key,
          "DATASET",
          String.valueOf(datasetId),
          null,
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private Asset resolveDatasetFieldAsset(Asset dataset, long datasetId, String fieldId) {
    String key = datasetFieldAssetKey(datasetId, fieldId);
    try {
      return lineage.requireAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("fieldId", fieldId);
      properties.put("lineageRegistration", "ANALYSIS_FALLBACK");
      return lineage.registerAsset(new AssetSpec(
          key,
          AssetType.DATASET_FIELD,
          fieldId,
          "DATASET",
          datasetId + ":" + fieldId,
          dataset.id(),
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private Map<String, Object> datasetRelationProperties(AnalysisAsset analysis) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lineageLevel", "DATASET");
    properties.put("analysisId", String.valueOf(analysis.id()));
    properties.put("datasetId", String.valueOf(analysis.datasetId()));
    properties.put("chartType", analysis.chartType().name());
    return properties;
  }

  private Map<String, Object> fieldRelationProperties(
      AnalysisAsset analysis,
      String fieldId,
      FieldUsage usage) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lineageLevel", "DATASET_FIELD");
    properties.put("analysisId", String.valueOf(analysis.id()));
    properties.put("datasetId", String.valueOf(analysis.datasetId()));
    properties.put("fieldId", fieldId);
    properties.put("usageRoles", usage.roles());
    if (!usage.metricAggregations().isEmpty()) {
      properties.put("metricAggregations", usage.metricAggregations());
    }
    if (!usage.sortAggregations().isEmpty()) {
      properties.put("sortAggregations", usage.sortAggregations());
    }
    return properties;
  }

  /** CHART(可复用分析)资产键全仓唯一出处(asset 台账与血缘同源,D6)。 */
  public static String chartAssetKey(long analysisId) {
    return "chart:analysis:" + analysisId;
  }

  static String datasetAssetKey(long datasetId) {
    return "dataset:" + datasetId;
  }

  static String datasetFieldAssetKey(long datasetId, String fieldId) {
    return "dataset-field:" + datasetId + ":" + fieldId;
  }
}
