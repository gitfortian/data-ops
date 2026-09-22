package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DerivedAwareSqlColumnLineageParserTest {

  private final DerivedAwareSqlColumnLineageParser parser =
      new DerivedAwareSqlColumnLineageParser();

  @Test
  void propagatesDirectColumnThroughCteWithoutPersistingVirtualAssets() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.active_users (user_id)
        WITH active AS (
          SELECT o.user_id
          FROM ods.orders o
          WHERE o.status = 'PAID'
        )
        SELECT a.user_id
        FROM active a
        """);

    assertEquals(1, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "user_id",
        "dws.active_users",
        "user_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertNoVirtualTableMappings(result.mappings(), "active");
  }

  @Test
  void preservesAggregationKindAcrossCteBoundary() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.user_sales (user_id, gmv)
        WITH summary AS (
          SELECT o.user_id, SUM(o.amount) AS gmv
          FROM ods.orders o
          GROUP BY o.user_id
        )
        SELECT s.user_id, s.gmv
        FROM summary s
        """);

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "user_id",
        "dws.user_sales",
        "user_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(),
        "ods.orders",
        "amount",
        "dws.user_sales",
        "gmv",
        SqlColumnLineageParser.MappingKind.AGGREGATION);
    assertTrue(result.mappings().stream()
        .filter(mapping -> mapping.targetColumnName().equals("gmv"))
        .allMatch(mapping -> mapping.expression().contains("SUM")));
  }

  @Test
  void propagatesTransformationAcrossChainedCtes() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.taxed_orders (order_id, taxed_amount)
        WITH base AS (
          SELECT o.id AS order_id, o.amount
          FROM ods.orders o
        ),
        taxed AS (
          SELECT b.order_id, b.amount * 1.1 AS taxed_amount
          FROM base b
        )
        SELECT t.order_id, t.taxed_amount
        FROM taxed t
        """);

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "id",
        "dws.taxed_orders",
        "order_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(),
        "ods.orders",
        "amount",
        "dws.taxed_orders",
        "taxed_amount",
        SqlColumnLineageParser.MappingKind.TRANSFORMATION);
    assertNoVirtualTableMappings(result.mappings(), "base", "taxed");
  }

  @Test
  void propagatesColumnsThroughFromSubquery() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.order_summary (order_id, gmv)
        SELECT s.order_id, s.gmv
        FROM (
          SELECT o.id AS order_id, SUM(o.amount) AS gmv
          FROM ods.orders o
          GROUP BY o.id
        ) s
        """);

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "id",
        "dws.order_summary",
        "order_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(),
        "ods.orders",
        "amount",
        "dws.order_summary",
        "gmv",
        SqlColumnLineageParser.MappingKind.AGGREGATION);
  }

  @Test
  void resolvesJoinBetweenDerivedTableAndPhysicalTable() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            INSERT INTO dws.order_customer (amount, customer_name)
            SELECT s.amount, c.name
            FROM (
              SELECT o.customer_id, o.amount
              FROM ods.orders o
            ) s
            JOIN dim.customer c ON c.id = s.customer_id
            """,
        schema(Map.of(
            "ods.orders", List.of("customer_id", "amount"),
            "dim.customer", List.of("id", "name"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "amount",
        "dws.order_customer",
        "amount",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(),
        "dim.customer",
        "name",
        "dws.order_customer",
        "customer_name",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void expandsStarFromCteUsingPhysicalSchemaOrigins() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            INSERT INTO dws.orders_copy (id, amount)
            WITH base AS (
              SELECT * FROM ods.orders
            )
            SELECT b.*
            FROM base b
            """,
        schema(Map.of("ods.orders", List.of("id", "amount"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "id",
        "dws.orders_copy",
        "id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(),
        "ods.orders",
        "amount",
        "dws.orders_copy",
        "amount",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void mergesPhysicalOriginsFromUnionInsideCte() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.all_orders (id)
        WITH all_orders AS (
          SELECT o.id FROM ods.orders o
          UNION ALL
          SELECT a.id FROM archive.orders a
        )
        SELECT x.id
        FROM all_orders x
        """);

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "id",
        "dws.all_orders",
        "id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(),
        "archive.orders",
        "id",
        "dws.all_orders",
        "id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void recursiveCteDoesNotBecomeFakePhysicalTable() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.sequence_result (n)
        WITH RECURSIVE seq(n) AS (
          SELECT 1
          UNION ALL
          SELECT n + 1 FROM seq WHERE n < 3
        )
        SELECT s.n FROM seq s
        """);

    assertTrue(result.mappings().isEmpty());
    assertTrue(result.unresolvedReferenceCount() > 0);
    assertNoVirtualTableMappings(result.mappings(), "seq");
  }

  @Test
  void nonDerivedSqlStillUsesBaselineParserBehavior() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.sales (order_id, amount)
        SELECT o.id, o.amount
        FROM ods.orders o
        """);

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(),
        "ods.orders",
        "id",
        "dws.sales",
        "order_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  private static SqlColumnLineageParser.SchemaProvider schema(
      Map<String, List<String>> definitions) {
    Map<String, List<SqlColumnLineageParser.SchemaColumn>> schemas = new LinkedHashMap<>();
    definitions.forEach((table, columns) -> {
      List<SqlColumnLineageParser.SchemaColumn> mapped = new ArrayList<>();
      for (int i = 0; i < columns.size(); i++) {
        mapped.add(new SqlColumnLineageParser.SchemaColumn(columns.get(i), i + 1));
      }
      schemas.put(table, List.copyOf(mapped));
    });
    return table -> schemas.getOrDefault(table.canonicalName(), List.of());
  }

  private static void assertMapping(
      List<SqlColumnLineageParser.ColumnMapping> mappings,
      String sourceTable,
      String sourceColumn,
      String targetTable,
      String targetColumn,
      SqlColumnLineageParser.MappingKind kind) {
    assertTrue(
        mappings.stream().anyMatch(mapping ->
            mapping.sourceTable().canonicalName().equals(sourceTable)
                && mapping.sourceColumnName().equals(sourceColumn)
                && mapping.targetTable().canonicalName().equals(targetTable)
                && mapping.targetColumnName().equals(targetColumn)
                && mapping.mappingKind() == kind),
        () -> "Missing mapping "
            + sourceTable + "." + sourceColumn
            + " -> " + targetTable + "." + targetColumn
            + " (" + kind + ") in " + mappings);
  }

  private static void assertNoVirtualTableMappings(
      List<SqlColumnLineageParser.ColumnMapping> mappings,
      String... virtualNames) {
    for (String virtualName : virtualNames) {
      assertFalse(
          mappings.stream().anyMatch(mapping ->
              mapping.sourceTable().canonicalName().equalsIgnoreCase(virtualName)),
          () -> "Virtual relation leaked into physical lineage mappings: " + virtualName);
    }
  }
}
