package io.yak.ops.business.lineage.analysis.sql;

import java.util.List;
import java.util.Objects;

/**
 * Source-neutral contract for resolving a read-only SQL projection to physical source columns.
 *
 * <p>The Lineage module owns only this analysis role and its source-neutral values. Concrete SQL
 * parser implementations remain in authoring/runtime modules so Dataset and future consumers can
 * reuse projection lineage without depending on data-development or a parser library.
 */
public interface SqlProjectionLineageAnalyzer {

  ProjectionResult analyze(String sql, SchemaProvider schemaProvider);

  default ProjectionResult analyze(String sql) {
    return analyze(sql, SchemaProvider.none());
  }

  enum MappingKind {
    IDENTITY,
    TRANSFORMATION,
    AGGREGATION
  }

  record TableRef(
      String canonicalName,
      String qualifiedName,
      String databaseName,
      String schemaName,
      String tableName) {

    public TableRef {
      Objects.requireNonNull(canonicalName, "canonicalName");
      Objects.requireNonNull(qualifiedName, "qualifiedName");
      Objects.requireNonNull(tableName, "tableName");
    }
  }

  record SchemaColumn(String name, Integer ordinalPosition) {
  }

  @FunctionalInterface
  interface SchemaProvider {
    List<SchemaColumn> columns(TableRef table);

    static SchemaProvider none() {
      return table -> List.of();
    }
  }

  record ProjectionMapping(
      TableRef sourceTable,
      String sourceColumnName,
      String outputColumnName,
      MappingKind mappingKind,
      String expression,
      int outputOrdinal,
      int sourceOrdinal) {

    public ProjectionMapping {
      Objects.requireNonNull(sourceTable, "sourceTable");
      Objects.requireNonNull(sourceColumnName, "sourceColumnName");
      Objects.requireNonNull(outputColumnName, "outputColumnName");
      Objects.requireNonNull(mappingKind, "mappingKind");
    }
  }

  record ProjectionResult(
      List<ProjectionMapping> mappings,
      int candidateOutputCount,
      int unresolvedReferenceCount) {

    public ProjectionResult {
      mappings = mappings == null ? List.of() : List.copyOf(mappings);
      if (candidateOutputCount < 0) throw new IllegalArgumentException("candidateOutputCount < 0");
      if (unresolvedReferenceCount < 0) {
        throw new IllegalArgumentException("unresolvedReferenceCount < 0");
      }
    }
  }
}
