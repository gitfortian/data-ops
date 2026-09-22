package io.yak.ops.business.development.lineage.analysis;

import io.yak.ops.business.development.service.SqlColumnLineageParser;
import io.yak.ops.business.development.service.SqlTableLineageParser;
import io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reuses the data-development SQL lineage engine for source-neutral SELECT projection analysis. */
@Component
public class DevelopmentSqlProjectionLineageAnalyzer implements SqlProjectionLineageAnalyzer {

  private static final String SYNTHETIC_TARGET = "__yak_dataset_projection__";

  private final SqlColumnLineageParser parser;

  public DevelopmentSqlProjectionLineageAnalyzer(SqlColumnLineageParser parser) {
    this.parser = parser;
  }

  @Override
  public ProjectionResult analyze(String sql, SchemaProvider schemaProvider) {
    if (sql == null || sql.isBlank()) {
      return new ProjectionResult(List.of(), 0, 0);
    }

    String normalized = stripTerminalSemicolon(sql);
    SqlColumnLineageParser.SchemaProvider provider = adapt(schemaProvider);
    SqlColumnLineageParser.ParseResult parsed =
        parser.parse("CREATE TABLE " + SYNTHETIC_TARGET + " AS " + normalized, provider);

    List<ProjectionMapping> mappings = new ArrayList<>();
    for (SqlColumnLineageParser.ColumnMapping mapping : parsed.mappings()) {
      if (mapping.targetTable() == null
          || !SYNTHETIC_TARGET.equalsIgnoreCase(mapping.targetTable().canonicalName())) {
        continue;
      }
      mappings.add(
          new ProjectionMapping(
              table(mapping.sourceTable()),
              mapping.sourceColumnName(),
              mapping.targetColumnName(),
              MappingKind.valueOf(mapping.mappingKind().name()),
              mapping.expression(),
              mapping.outputOrdinal(),
              mapping.sourceOrdinal()));
    }

    return new ProjectionResult(
        mappings, parsed.candidateOutputCount(), parsed.unresolvedReferenceCount());
  }

  private SqlColumnLineageParser.SchemaProvider adapt(SchemaProvider schemaProvider) {
    SchemaProvider actual = schemaProvider == null ? SchemaProvider.none() : schemaProvider;
    return table -> {
      List<SchemaColumn> columns = actual.columns(table(table));
      if (columns == null || columns.isEmpty()) return List.of();
      return columns.stream()
          .filter(column -> column != null && column.name() != null && !column.name().isBlank())
          .map(
              column ->
                  new SqlColumnLineageParser.SchemaColumn(
                      column.name(), column.ordinalPosition()))
          .toList();
    };
  }

  private TableRef table(SqlTableLineageParser.TableRef table) {
    return new TableRef(
        table.canonicalName(),
        table.qualifiedName(),
        table.databaseName(),
        table.schemaName(),
        table.tableName());
  }

  private String stripTerminalSemicolon(String sql) {
    String value = sql.trim();
    while (value.endsWith(";")) {
      value = value.substring(0, value.length() - 1).trim();
    }
    if (value.isEmpty()) throw new IllegalArgumentException("SQL projection 不能为空");
    return value;
  }
}
