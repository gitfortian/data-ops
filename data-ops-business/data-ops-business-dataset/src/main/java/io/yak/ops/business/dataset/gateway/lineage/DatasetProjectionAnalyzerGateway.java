package io.yak.ops.business.dataset.gateway.lineage;

import java.util.List;

/** Dataset-owned SQL projection lineage capability. */
public interface DatasetProjectionAnalyzerGateway {

  Analysis analyze(String dataSourceId, String sql);

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
      String tableName) {}

  record ProjectionMapping(
      TableRef sourceTable,
      String sourceColumnName,
      String outputColumnName,
      MappingKind mappingKind,
      String expression,
      int outputOrdinal,
      int sourceOrdinal) {}

  record ProjectionResult(
      List<ProjectionMapping> mappings,
      int candidateOutputCount,
      int unresolvedReferenceCount) {
    public ProjectionResult {
      mappings = mappings == null ? List.of() : List.copyOf(mappings);
    }
  }

  record Analysis(boolean available, ProjectionResult result) {
    public static Analysis unavailable() {
      return new Analysis(false, null);
    }

    public static Analysis available(ProjectionResult result) {
      return new Analysis(true, result);
    }
  }
}
