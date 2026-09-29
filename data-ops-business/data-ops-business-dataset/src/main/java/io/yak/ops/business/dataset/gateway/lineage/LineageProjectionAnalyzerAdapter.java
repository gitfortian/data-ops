package io.yak.ops.business.dataset.gateway.lineage;

import io.yak.ops.business.dataset.gateway.datasource.DatasetCatalogGateway;
import io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Adapts the optional lineage-core analyzer and typed Datasource catalog to Dataset-owned values. */
@Component
public class LineageProjectionAnalyzerAdapter implements DatasetProjectionAnalyzerGateway {

  private final ObjectProvider<SqlProjectionLineageAnalyzer> analyzerProvider;
  private final DatasetCatalogGateway catalogGateway;

  public LineageProjectionAnalyzerAdapter(
      ObjectProvider<SqlProjectionLineageAnalyzer> analyzerProvider,
      DatasetCatalogGateway catalogGateway) {
    this.analyzerProvider = analyzerProvider;
    this.catalogGateway = catalogGateway;
  }

  @Override
  public Analysis analyze(String dataSourceId, String sql) {
    SqlProjectionLineageAnalyzer analyzer = analyzerProvider.getIfAvailable();
    if (analyzer == null) {
      return Analysis.unavailable();
    }
    SqlProjectionLineageAnalyzer.ProjectionResult result =
        analyzer.analyze(sql, schemaProvider(dataSourceId));
    return Analysis.available(toResult(result));
  }

  private SqlProjectionLineageAnalyzer.SchemaProvider schemaProvider(String dataSourceId) {
    if (dataSourceId == null || dataSourceId.isBlank()) {
      return SqlProjectionLineageAnalyzer.SchemaProvider.none();
    }

    final long numericDataSourceId;
    try {
      numericDataSourceId = Long.parseLong(dataSourceId);
    } catch (NumberFormatException exception) {
      return SqlProjectionLineageAnalyzer.SchemaProvider.none();
    }

    Map<String, List<SqlProjectionLineageAnalyzer.SchemaColumn>> cache = new LinkedHashMap<>();
    return table ->
        cache.computeIfAbsent(
            table.canonicalName(), ignored -> loadSchemaColumns(numericDataSourceId, table));
  }

  private List<SqlProjectionLineageAnalyzer.SchemaColumn> loadSchemaColumns(
      long dataSourceId, SqlProjectionLineageAnalyzer.TableRef table) {
    List<DatasetCatalogGateway.CatalogColumn> columns =
        catalogGateway.listColumns(
            dataSourceId, table.databaseName(), table.schemaName(), table.tableName());
    if (columns.isEmpty() && table.databaseName() == null && table.schemaName() != null) {
      columns =
          catalogGateway.listColumns(
              dataSourceId, table.schemaName(), null, table.tableName());
    }
    return columns.stream()
        .filter(column -> column.name() != null && !column.name().isBlank())
        .map(
            column ->
                new SqlProjectionLineageAnalyzer.SchemaColumn(
                    column.name(), column.ordinalPosition()))
        .toList();
  }

  private ProjectionResult toResult(SqlProjectionLineageAnalyzer.ProjectionResult result) {
    return new ProjectionResult(
        result.mappings().stream().map(this::toMapping).toList(),
        result.candidateOutputCount(),
        result.unresolvedReferenceCount());
  }

  private ProjectionMapping toMapping(SqlProjectionLineageAnalyzer.ProjectionMapping mapping) {
    SqlProjectionLineageAnalyzer.TableRef table = mapping.sourceTable();
    return new ProjectionMapping(
        new TableRef(
            table.canonicalName(),
            table.qualifiedName(),
            table.databaseName(),
            table.schemaName(),
            table.tableName()),
        mapping.sourceColumnName(),
        mapping.outputColumnName(),
        MappingKind.valueOf(mapping.mappingKind().name()),
        mapping.expression(),
        mapping.outputOrdinal(),
        mapping.sourceOrdinal());
  }
}
