package io.yak.ops.business.lineage.domain;

import com.fasterxml.jackson.databind.JsonNode;

/** Validated domain write model for registering a metadata asset. */
public record LineageAssetDraft(
    Long projectId,
    String assetKey,
    LineageAssetType assetType,
    String name,
    String sourceType,
    String sourceId,
    Long parentAssetId,
    String dataSourceId,
    String databaseName,
    String schemaName,
    String tableName,
    String columnName,
    JsonNode properties) {

  public LineageAssetDraft(
      String assetKey,
      LineageAssetType assetType,
      String name,
      String sourceType,
      String sourceId,
      Long parentAssetId,
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName,
      String columnName,
      JsonNode properties) {
    this(
        null,
        assetKey,
        assetType,
        name,
        sourceType,
        sourceId,
        parentAssetId,
        dataSourceId,
        databaseName,
        schemaName,
        tableName,
        columnName,
        properties);
  }
}
