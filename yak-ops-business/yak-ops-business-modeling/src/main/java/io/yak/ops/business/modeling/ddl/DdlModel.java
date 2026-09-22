package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.List;
import java.util.Map;

/** Input snapshot of a model's structure handed to a dialect DDL generator. */
public record DdlModel(
    String tableName,
    String tableComment,
    List<ColumnDefinition> columns,
    List<String> primaryKey,
    List<IndexDefinition> indexes,
    String partitionType,
    List<String> partitionColumns,
    String partitionExpression,
    Map<String, String> tableProperties) {}
