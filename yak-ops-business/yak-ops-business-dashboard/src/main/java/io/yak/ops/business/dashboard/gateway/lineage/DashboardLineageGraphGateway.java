package io.yak.ops.business.dashboard.gateway.lineage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Dashboard-owned boundary for projecting effective snapshots into the shared lineage graph. */
public interface DashboardLineageGraphGateway {

  void clearRelationsByEvidence(String evidenceSourceType, String evidenceId);

  Asset registerAsset(AssetSpec spec);

  Asset requireAssetByKey(String assetKey);

  void registerRelation(RelationSpec spec);

  enum AssetType {
    DASHBOARD,
    CHART,
    DATASET,
    DATASET_FIELD
  }

  enum RelationType {
    CONTAINS,
    CONSUMES
  }

  record Asset(long id, String assetKey) {
  }

  record AssetSpec(
      String assetKey,
      AssetType assetType,
      String name,
      String sourceType,
      String sourceId,
      Long parentAssetId,
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName,
      String columnName,
      Map<String, Object> properties) {
  }

  record RelationSpec(
      long sourceAssetId,
      long targetAssetId,
      RelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      Map<String, Object> properties) {
  }
}
