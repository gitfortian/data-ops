package io.yak.ops.business.modeling.api;

import java.util.List;

/** Authorized fixed-table metadata for source-field suggestions; never reads table rows. */
public interface MappingSuggestionQueryApi {
  Context require(long modelId, String targetColumn, long datasourceId, String database, String table, String keyword);

  record Context(String modelName, String dialect, String targetColumn, String targetType,
      String targetDescription, String definition, String sourceDefinition,
      List<SourceColumn> columns, boolean truncated) {}
  record SourceColumn(String name, String type, String description, boolean nullable) {}
}
