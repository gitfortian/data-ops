package io.yak.ops.business.modeling.structure;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for a model's physical table structure. */
public interface ModelStructureRepository {

  /** Physical table name; empty means "fall back to model_code". */
  Optional<String> findTableName(Long modelId);

  Optional<String> findTableComment(Long modelId);

  /** Updates table name/comment on the live model row. */
  boolean updateTableInfo(Long modelId, String tableName, String tableComment);

  /** Updates pk/partition/table-properties attributes on the live model row (ticket 06). */
  boolean updateTableAttributes(
      Long modelId,
      String primaryKeyJson,
      String partitionType,
      String partitionColumnsJson,
      String partitionExpr,
      String tablePropertiesJson);

  Optional<String> findPrimaryKeyJson(Long modelId);

  Optional<String> findPartitionType(Long modelId);

  Optional<String> findPartitionColumnsJson(Long modelId);

  Optional<String> findPartitionExpr(Long modelId);

  Optional<String> findTablePropertiesJson(Long modelId);

  List<ColumnDefinition> findColumns(Long modelId);

  /** Full replace: removes stored columns of the model, inserts the given ordered list. */
  void replaceColumns(Long modelId, List<ColumnDefinition> columns);

  /** 44 治理回填:只改单列的标准字段关联(不做整表替换);列不存在返回 false。 */
  boolean updateColumnStdField(Long modelId, String columnName, Long stdFieldId);

  List<IndexDefinition> findIndexes(Long modelId);

  /** Full replace: removes stored indexes of the model, inserts the given list. */
  void replaceIndexes(Long modelId, List<IndexDefinition> indexes);
}
