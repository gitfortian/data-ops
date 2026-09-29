package io.yak.ops.business.modeling.structure;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import io.yak.ops.business.modeling.domain.Model;
import java.util.List;
import java.util.Map;

/** Read/save view of a model's physical table structure. */
public record StructureView(
    Long modelId,
    String modelCode,
    String modelName,
    String dialect,
    String status,
    String modelDescription,
    String tableName,
    String tableComment,
    List<ColumnView> columns,
    List<String> primaryKey,
    List<IndexView> indexes,
    PartitionView partition,
    Map<String, String> tableProperties) {

  public record ColumnView(
      Long id,
      String columnName,
      String dataType,
      Integer length,
      Integer scale,
      Boolean nullable,
      String defaultValue,
      String comment,
      String businessDescription,
      Integer sortOrder,
      Long stdTypeId,
      Long stdNamingId,
      String stdCodeSetCode,
      Long stdUnitId,
      Long stdCaliberId,
      Long stdSecurityId,
      Long stdFieldId,
      String fieldRole,
      String aggregateFunc,
      String transformExpr) {}

  public record IndexView(
      Long id,
      String indexName,
      Boolean uniqueIndex,
      String indexType,
      List<String> columns) {}

  public record PartitionView(String type, List<String> columns, String expression) {}

  public static StructureView of(
      Model model,
      String tableName,
      String tableComment,
      List<ColumnDefinition> definitions,
      List<String> primaryKey,
      List<IndexDefinition> indexes,
      PartitionView partition,
      Map<String, String> tableProperties) {
    List<ColumnView> columns =
        definitions == null
            ? List.of()
            : definitions.stream()
                .map(column ->
                    new ColumnView(
                        column.id(),
                        column.columnName(),
                        column.dataType(),
                        column.length(),
                        column.scale(),
                        column.nullable(),
                        column.defaultValue(),
                        column.comment(),
                        column.businessDescription(),
                        column.sortOrder(),
                        column.stdTypeId(),
                        column.stdNamingId(),
                        column.stdCodeSetCode(),
                        column.stdUnitId(),
                        column.stdCaliberId(),
                        column.stdSecurityId(),
                        column.stdFieldId(),
                        column.fieldRole(),
                        column.aggregateFunc(),
                        column.transformExpr()))
                .toList();
    List<IndexView> indexViews =
        indexes == null
            ? List.of()
            : indexes.stream()
                .map(index ->
                    new IndexView(
                        index.id(),
                        index.indexName(),
                        index.uniqueIndex(),
                        index.indexType(),
                        index.columns()))
                .toList();
    return new StructureView(
        model.id(),
        model.code(),
        model.name(),
        model.dialect() == null ? null : model.dialect().name(),
        model.status() == null ? null : model.status().name(),
        model.description(),
        tableName,
        tableComment,
        columns,
        primaryKey,
        indexViews,
        partition,
        tableProperties);
  }
}
