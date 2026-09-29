package io.yak.ops.business.dataset.gateway.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

/** Dataset-owned boundary for registering derived metadata in the shared lineage graph. */
public interface DatasetLineageGraphGateway {

  void clearRelationsByEvidence(String evidenceSourceType, String evidenceId);

  Asset registerAsset(AssetSpec spec);

  Asset requireAssetByKey(String assetKey);

  void registerRelation(RelationSpec spec);

  enum AssetType {
    DATASET,
    DATASET_FIELD,
    SQL_TASK,
    TABLE,
    COLUMN
  }

  enum RelationType {
    CONTAINS,
    DERIVES_FROM
  }

  record Asset(long id, String assetKey) {}

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
      JsonNode properties) {}

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
      JsonNode properties) {}
}
