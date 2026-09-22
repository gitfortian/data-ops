package io.yak.ops.business.lineage.controller.v1.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** HTTP response contracts for lineage APIs. */
public final class LineageViews {

  private LineageViews() {
  }

  public record AssetView(
      String id,
      String assetKey,
      LineageAssetType assetType,
      String name,
      String sourceType,
      String sourceId,
      String parentAssetId,
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName,
      String columnName,
      JsonNode properties,
      Instant createTime,
      Instant updateTime) {
  }

  public record RelationView(
      String id,
      String sourceAssetId,
      String targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      JsonNode properties,
      Instant createTime,
      Instant updateTime) {
  }

  public record GraphView(
      AssetView root,
      LineageDirection direction,
      int depth,
      List<AssetView> nodes,
      List<RelationView> relations) {
  }
}
