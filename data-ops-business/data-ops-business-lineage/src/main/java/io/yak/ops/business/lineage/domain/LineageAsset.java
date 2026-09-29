package io.yak.ops.business.lineage.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Stable identity for an object participating in the Yak Ops metadata graph. */
public record LineageAsset(
    long id,
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
    JsonNode properties,
    Instant createTime,
    Instant updateTime) {

  /** Compatibility constructor for callers compiled against the pre-Project projection model. */
  public LineageAsset(
      long id,
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
      JsonNode properties,
      Instant createTime,
      Instant updateTime) {
    this(
        id,
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
        properties,
        createTime,
        updateTime);
  }
}
