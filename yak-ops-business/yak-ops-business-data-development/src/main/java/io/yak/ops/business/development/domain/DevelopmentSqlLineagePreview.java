package io.yak.ops.business.development.domain;

import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import java.util.List;
import java.util.Map;

/** Read-only lineage result generated from the current SQL editor content. */
public record DevelopmentSqlLineagePreview(
    String status,
    String dataSourceId,
    int statementCount,
    int inputTableCount,
    int outputTableCount,
    int columnMappingCount,
    int candidateOutputColumnCount,
    int unresolvedColumnReferenceCount,
    String parseError,
    String columnParseError,
    PreviewGraph graph,
    List<ColumnMapping> columnMappings) {

  public DevelopmentSqlLineagePreview {
    columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
  }

  public record PreviewGraph(
      PreviewAsset root,
      LineageDirection direction,
      int depth,
      List<PreviewAsset> nodes,
      List<PreviewRelation> relations) {

    public PreviewGraph {
      nodes = nodes == null ? List.of() : List.copyOf(nodes);
      relations = relations == null ? List.of() : List.copyOf(relations);
    }
  }

  /** Mirrors the persisted LineageAsset JSON shape while keeping preview identities string-based. */
  public record PreviewAsset(
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
      Map<String, Object> properties) {
  }

  /** Mirrors the persisted LineageRelation JSON shape without writing derived metadata. */
  public record PreviewRelation(
      String id,
      String sourceAssetId,
      String targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      Map<String, Object> properties) {
  }

  public record ColumnMapping(
      String sourceTable,
      String sourceColumn,
      String sourceDataType,
      String targetTable,
      String targetColumn,
      String targetDataType,
      String mappingKind,
      String expression,
      int outputOrdinal,
      int sourceOrdinal) {
  }
}
