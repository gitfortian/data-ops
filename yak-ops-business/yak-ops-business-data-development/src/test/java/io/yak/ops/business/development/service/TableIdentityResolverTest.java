package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class TableIdentityResolverTest {
  private final TableIdentityResolver resolver = new TableIdentityResolver();

  @Test
  void unqualifiedNameUsesExecutionContextAndDatasource() {
    var identity = resolve(table("orders", null, null, "orders"), "1", "sales", "public", "postgresql");
    assertEquals("table:1:sales.public.orders", identity.assetKey());
  }

  @Test
  void databasesSchemasAndDatasourcesCannotCollide() {
    var table = table("orders", null, null, "orders");
    assertNotEquals(resolve(table, "1", "sales", "public", "postgresql").assetKey(),
        resolve(table, "1", "marketing", "public", "postgresql").assetKey());
    assertNotEquals(resolve(table, "1", "sales", "public", "postgresql").assetKey(),
        resolve(table, "1", "sales", "private", "postgresql").assetKey());
    assertNotEquals(resolve(table, "1", "sales", "public", "postgresql").assetKey(),
        resolve(table, "2", "sales", "public", "postgresql").assetKey());
  }

  @Test
  void explicitTwoAndThreePartNamesAreNotOverwritten() {
    assertEquals("table:1:sales.archive.orders",
        resolve(table("archive.orders", null, "archive", "orders"),
            "1", "sales", "public", "postgresql").assetKey());
    assertEquals("table:1:warehouse.archive.orders",
        resolve(table("warehouse.archive.orders", "warehouse", "archive", "orders"),
            "1", "sales", "public", "postgresql").assetKey());
  }

  @Test
  void twoPartMeaningFollowsDialect() {
    var twoPart = table("archive.orders", null, "archive", "orders");
    assertEquals("table:1:sales.archive.orders",
        resolve(twoPart, "1", "sales", "public", "postgresql").assetKey());
    assertEquals("table:1:archive..orders",
        resolve(twoPart, "1", "sales", "public", "mysql").assetKey());
  }

  @Test
  void canonicalDialectNamesUseTheSameResolutionFamilies() {
    var twoPart = table("archive.orders", null, "archive", "orders");
    assertEquals("table:1:sales.archive.orders",
        resolve(twoPart, "1", "sales", "public", "POSTGRE_SQL").assetKey());
    assertEquals("table:1:archive..orders",
        resolve(twoPart, "1", "sales", "public", "STARROCKS").assetKey());
  }

  @Test
  void legacyContextIsIsolatedFromConfirmedAssets() {
    var identity = resolve(table("orders", null, null, "orders"), "1", null, null, null);
    assertEquals("table:unresolved:1:..orders", identity.assetKey());
  }

  @Test
  void canonicalizationIsStableForCaseAndSpecialCharacters() {
    var identity = resolve(table("\"Sales Schema\".\"Order-Items\"", null,
        "Sales Schema", "Order-Items"), "DS-A", null, null, "postgresql");
    assertEquals("table:ds-a:.sales schema.order-items", identity.assetKey());
  }

  @Test
  void assetKeyDelegatesToTheSharedCommonGenerator() {
    // 键的拼法已下沉 common（ticket 134）：目录与血缘共用一把，本地再抄一遍就会静默裂成两个节点。
    var identities =
        java.util.List.of(
            resolve(table("orders", null, null, "orders"), "1", "sales", "public", "postgresql"),
            resolve(table("orders", null, null, "orders"), "1", null, null, null),
            resolve(table("warehouse.archive.orders", "warehouse", "archive", "orders"),
                "1", "sales", "public", "mysql"),
            resolve(table("\"Sales Schema\".\"Order-Items\"", null, "Sales Schema", "Order-Items"),
                "DS-A", null, null, "postgresql"));

    for (var identity : identities) {
      assertEquals(
          io.yak.ops.common.util.metadata.PhysicalTableAssetKey.of(
              identity.dataSourceId(), identity.databaseName(),
              identity.schemaName(), identity.tableName()),
          identity.assetKey());
    }
  }

  private TableIdentityResolver.PhysicalTableIdentity resolve(
      SqlTableLineageParser.TableRef table, String ds, String database, String schema, String dialect) {
    return resolver.resolve(table, new TableIdentityResolver.ResolutionContext(
        ds, database, schema, TableIdentityResolver.SqlDialect.from(dialect)));
  }

  private static SqlTableLineageParser.TableRef table(
      String qualified, String database, String schema, String table) {
    return new SqlTableLineageParser.TableRef(
        qualified.toLowerCase(), qualified, database, schema, table);
  }
}
