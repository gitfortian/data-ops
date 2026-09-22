package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SqlTableLineageParserTest {

  private final SqlTableLineageParser parser = new SqlTableLineageParser();

  @Test
  void extractsInsertSelectInputsAndOutput() {
    SqlTableLineageParser.ParseResult result = parser.parse(
        """
        INSERT INTO dws.sales_daily (user_id, amount)
        SELECT o.user_id, o.amount
        FROM ods.orders o
        JOIN dim.users u ON u.id = o.user_id
        WHERE o.status = 'PAID'
        """);

    assertEquals(Set.of("ods.orders", "dim.users"), canonical(result.inputs()));
    assertEquals(Set.of("dws.sales_daily"), canonical(result.outputs()));
    assertEquals(1, result.statementCount());
  }

  @Test
  void excludesCteAliasFromPhysicalInputs() {
    SqlTableLineageParser.ParseResult result = parser.parse(
        """
        WITH recent_orders AS (
          SELECT user_id, amount
          FROM ods.orders
        )
        SELECT r.user_id, r.amount
        FROM recent_orders r
        JOIN dim.users u ON u.id = r.user_id
        """);

    assertEquals(Set.of("ods.orders", "dim.users"), canonical(result.inputs()));
    assertTrue(result.outputs().isEmpty());
  }

  @Test
  void treatsCreateTableAsSelectAsWriteTarget() {
    SqlTableLineageParser.ParseResult result = parser.parse(
        "CREATE TABLE dwd.new_orders AS SELECT * FROM ods.orders");

    assertEquals(Set.of("ods.orders"), canonical(result.inputs()));
    assertEquals(Set.of("dwd.new_orders"), canonical(result.outputs()));
  }

  @Test
  void treatsUpdateAndDeleteTargetsAsReadWriteTables() {
    SqlTableLineageParser.ParseResult result = parser.parse(
        """
        UPDATE mart.orders SET status = 'DONE' WHERE id = 1;
        DELETE FROM mart.bad_orders WHERE id = 2;
        """);

    assertEquals(Set.of("mart.orders", "mart.bad_orders"), canonical(result.inputs()));
    assertEquals(Set.of("mart.orders", "mart.bad_orders"), canonical(result.outputs()));
    assertEquals(2, result.statementCount());
  }

  private static Set<String> canonical(java.util.List<SqlTableLineageParser.TableRef> tables) {
    return tables.stream()
        .map(SqlTableLineageParser.TableRef::canonicalName)
        .collect(Collectors.toSet());
  }
}
