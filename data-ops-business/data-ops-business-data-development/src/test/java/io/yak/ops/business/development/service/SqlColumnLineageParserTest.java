package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlColumnLineageParserTest {

  private final SqlColumnLineageParser parser = new SqlColumnLineageParser();

  @Test
  void parsesInsertSelectIdentityTransformationAndAggregation() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.sales_daily (user_id, gross_amount, order_count)
        SELECT
          o.user_id,
          o.quantity * o.price AS gross_amount,
          COUNT(o.id) AS order_count
        FROM ods.orders o
        GROUP BY o.user_id, o.quantity, o.price
        """);

    assertEquals(4, result.mappings().size());
    assertMapping(
        result.mappings(), "ods.orders", "user_id", "dws.sales_daily", "user_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "ods.orders", "quantity", "dws.sales_daily", "gross_amount",
        SqlColumnLineageParser.MappingKind.TRANSFORMATION);
    assertMapping(
        result.mappings(), "ods.orders", "price", "dws.sales_daily", "gross_amount",
        SqlColumnLineageParser.MappingKind.TRANSFORMATION);
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.sales_daily", "order_count",
        SqlColumnLineageParser.MappingKind.AGGREGATION);
    assertEquals(0, result.unresolvedReferenceCount());
  }

  @Test
  void resolvesQualifiedJoinColumnsWithoutCreatingCrossProductMappings() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.order_customer (order_id, customer_name)
        SELECT o.id, c.name
        FROM ods.orders o
        JOIN dim.customer c ON c.id = o.customer_id
        """);

    assertEquals(2, result.mappings().size());
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.order_customer", "order_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "dim.customer", "name", "dws.order_customer", "customer_name",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void leavesAmbiguousUnqualifiedJoinColumnUnresolvedWithoutSchema() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.ambiguous_result (id)
        SELECT id
        FROM ods.orders o
        JOIN dim.customer c ON c.id = o.customer_id
        """);

    assertTrue(result.mappings().isEmpty());
    assertEquals(1, result.unresolvedReferenceCount());
  }

  @Test
  void derivesCtasOutputNamesFromAliasesAndDirectColumns() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        CREATE TABLE dws.order_summary AS
        SELECT o.id AS order_id, o.amount * 1.1 AS taxed_amount
        FROM ods.orders o
        """);

    assertEquals(2, result.mappings().size());
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.order_summary", "order_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "ods.orders", "amount", "dws.order_summary", "taxed_amount",
        SqlColumnLineageParser.MappingKind.TRANSFORMATION);
  }

  @Test
  void parsesUpdateExpressionDependencies() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        UPDATE dws.sales_daily s
        SET gmv = s.net_amount + s.tax_amount
        """);

    assertEquals(2, result.mappings().size());
    assertMapping(
        result.mappings(), "dws.sales_daily", "net_amount", "dws.sales_daily", "gmv",
        SqlColumnLineageParser.MappingKind.TRANSFORMATION);
    assertMapping(
        result.mappings(), "dws.sales_daily", "tax_amount", "dws.sales_daily", "gmv",
        SqlColumnLineageParser.MappingKind.TRANSFORMATION);
  }

  @Test
  void mapsEveryUnionBranchToTheSameTargetColumn() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.all_ids (id)
        SELECT a.id FROM ods.orders a
        UNION ALL
        SELECT b.id FROM archive.orders b
        """);

    assertEquals(2, result.mappings().size());
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.all_ids", "id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "archive.orders", "id", "dws.all_ids", "id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void starProjectionFallsBackToTableLineageWithoutSchema() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.orders_copy (id)
        SELECT * FROM ods.orders
        """);

    assertTrue(result.mappings().isEmpty());
    assertEquals(1, result.unresolvedReferenceCount());
  }

  @Test
  void cteReferencesDoNotBecomeFakePhysicalColumnAssets() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.active_users (user_id)
        WITH active AS (
          SELECT user_id FROM ods.orders WHERE status = 'PAID'
        )
        SELECT a.user_id FROM active a
        """);

    assertTrue(result.mappings().isEmpty());
    assertTrue(result.unresolvedReferenceCount() > 0);
  }

  @Test
  void insertWithoutExplicitTargetColumnsStaysConservativeWithoutSchema() {
    SqlColumnLineageParser.ParseResult result = parser.parse("""
        INSERT INTO dws.orders_copy
        SELECT o.id FROM ods.orders o
        """);

    assertTrue(result.mappings().isEmpty());
    assertTrue(result.unresolvedReferenceCount() > 0);
  }

  @Test
  void schemaAwareStarExpansionMapsSourceColumnsByProjectionOrder() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            INSERT INTO dws.orders_copy (id, amount)
            SELECT * FROM ods.orders
            """,
        schema(Map.of("ods.orders", List.of("id", "amount"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.orders_copy", "id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "ods.orders", "amount", "dws.orders_copy", "amount",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void schemaAwareQualifiedStarExpandsOnlyReferencedAlias() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            INSERT INTO dws.customer_copy (customer_id, customer_name)
            SELECT c.*
            FROM ods.orders o
            JOIN dim.customer c ON c.id = o.customer_id
            """,
        schema(Map.of(
            "ods.orders", List.of("id", "customer_id"),
            "dim.customer", List.of("customer_id", "customer_name"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertTrue(result.mappings().stream()
        .allMatch(mapping -> mapping.sourceTable().canonicalName().equals("dim.customer")));
  }

  @Test
  void schemaAwareResolverDisambiguatesUniqueUnqualifiedJoinColumns() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            INSERT INTO dws.order_customer (order_id, customer_name)
            SELECT id, name
            FROM ods.orders o
            JOIN dim.customer c ON c.customer_id = o.customer_id
            """,
        schema(Map.of(
            "ods.orders", List.of("id", "customer_id", "amount"),
            "dim.customer", List.of("customer_id", "name"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.order_customer", "order_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "dim.customer", "name", "dws.order_customer", "customer_name",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void schemaAwareResolverKeepsColumnAmbiguousWhenMultipleTablesOwnIt() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            INSERT INTO dws.ambiguous_result (id)
            SELECT id
            FROM ods.orders o
            JOIN dim.customer c ON c.id = o.customer_id
            """,
        schema(Map.of(
            "ods.orders", List.of("id", "customer_id"),
            "dim.customer", List.of("id", "name"))));

    assertTrue(result.mappings().isEmpty());
    assertEquals(1, result.unresolvedReferenceCount());
  }

  @Test
  void schemaAwareInsertWithoutTargetListUsesPhysicalTargetOrdinal() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            INSERT INTO dws.orders_copy
            SELECT o.id, o.amount FROM ods.orders o
            """,
        schema(Map.of(
            "ods.orders", List.of("id", "amount"),
            "dws.orders_copy", List.of("order_id", "order_amount"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.orders_copy", "order_id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "ods.orders", "amount", "dws.orders_copy", "order_amount",
        SqlColumnLineageParser.MappingKind.IDENTITY);
  }

  @Test
  void schemaAwareCtasStarInfersTargetColumnNamesFromSourceSchema() {
    SqlColumnLineageParser.ParseResult result = parser.parse(
        """
            CREATE TABLE dws.orders_copy AS
            SELECT * FROM ods.orders
            """,
        schema(Map.of("ods.orders", List.of("id", "amount"))));

    assertEquals(2, result.mappings().size());
    assertEquals(0, result.unresolvedReferenceCount());
    assertMapping(
        result.mappings(), "ods.orders", "id", "dws.orders_copy", "id",
        SqlColumnLineageParser.MappingKind.IDENTITY);
    assertMapping(
        result.mappings(), "ods.orders", "amount", "dws.orders_copy", "amount",
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
}
