package io.yak.ops.business.development.lineage.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.development.service.DerivedAwareSqlColumnLineageParser;
import io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevelopmentSqlProjectionLineageAnalyzerTest {

  private final DevelopmentSqlProjectionLineageAnalyzer analyzer =
      new DevelopmentSqlProjectionLineageAnalyzer(new DerivedAwareSqlColumnLineageParser());

  @Test
  void analyzesSimpleProjectionWithoutCreatingARealTargetTable() {
    SqlProjectionLineageAnalyzer.ProjectionResult result =
        analyzer.analyze(
            """
            SELECT o.user_id, SUM(o.amount) AS gmv
            FROM ods.orders o
            GROUP BY o.user_id
            """);

    assertEquals(2, result.mappings().size());
    assertMapping(
        result,
        "ods.orders",
        "user_id",
        "user_id",
        SqlProjectionLineageAnalyzer.MappingKind.IDENTITY);
    assertMapping(
        result,
        "ods.orders",
        "amount",
        "gmv",
        SqlProjectionLineageAnalyzer.MappingKind.AGGREGATION);
  }

  @Test
  void preservesCteAggregationWhenProjectionIsFlattened() {
    SqlProjectionLineageAnalyzer.ProjectionResult result =
        analyzer.analyze(
            """
            WITH summary AS (
              SELECT user_id, SUM(amount) AS gmv
              FROM ods.orders
              GROUP BY user_id
            )
            SELECT user_id, gmv
            FROM summary
            """);

    assertEquals(2, result.mappings().size());
    assertMapping(
        result,
        "ods.orders",
        "amount",
        "gmv",
        SqlProjectionLineageAnalyzer.MappingKind.AGGREGATION);
    assertTrue(
        result.mappings().stream()
            .noneMatch(mapping -> mapping.sourceTable().canonicalName().equals("summary")));
  }

  @Test
  void expandsStarUsingSourceNeutralSchemaProvider() {
    SqlProjectionLineageAnalyzer.ProjectionResult result =
        analyzer.analyze(
            "SELECT * FROM ods.orders",
            schema(Map.of("ods.orders", List.of("id", "amount"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result,
        "ods.orders",
        "id",
        "id",
        SqlProjectionLineageAnalyzer.MappingKind.IDENTITY);
    assertMapping(
        result,
        "ods.orders",
        "amount",
        "amount",
        SqlProjectionLineageAnalyzer.MappingKind.IDENTITY);
  }

  private static SqlProjectionLineageAnalyzer.SchemaProvider schema(
      Map<String, List<String>> definitions) {
    Map<String, List<SqlProjectionLineageAnalyzer.SchemaColumn>> schemas = new LinkedHashMap<>();
    definitions.forEach(
        (table, columns) -> {
          List<SqlProjectionLineageAnalyzer.SchemaColumn> mapped = new ArrayList<>();
          for (int i = 0; i < columns.size(); i++) {
            mapped.add(new SqlProjectionLineageAnalyzer.SchemaColumn(columns.get(i), i + 1));
          }
          schemas.put(table, List.copyOf(mapped));
        });
    return table -> schemas.getOrDefault(table.canonicalName(), List.of());
  }

  private static void assertMapping(
      SqlProjectionLineageAnalyzer.ProjectionResult result,
      String sourceTable,
      String sourceColumn,
      String outputColumn,
      SqlProjectionLineageAnalyzer.MappingKind kind) {
    assertTrue(
        result.mappings().stream()
            .anyMatch(
                mapping ->
                    mapping.sourceTable().canonicalName().equals(sourceTable)
                        && mapping.sourceColumnName().equals(sourceColumn)
                        && mapping.outputColumnName().equals(outputColumn)
                        && mapping.mappingKind() == kind),
        () ->
            "Missing mapping "
                + sourceTable
                + "."
                + sourceColumn
                + " -> "
                + outputColumn
                + " ("
                + kind
                + ") in "
                + result.mappings());
  }
}
