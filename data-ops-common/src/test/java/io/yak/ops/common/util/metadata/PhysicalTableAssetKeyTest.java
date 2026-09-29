package io.yak.ops.common.util.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 共键契约（ticket 134）：这里的每个字面量都是 data-development 现网产出的键，<b>一个字节都不许变</b>。
 *
 * <p>下沉的动机就是这些串：目录与血缘共用 {@code uk (project_scope_id, asset_key)}，同一张表若拿到
 * 两种拼法就会裂成两个节点，而数据库不会报错（plan §2.4.3）。所以改动这段逻辑时，正确做法是
 * 让调用方一起变，而不是把某个用例的期望值改掉。
 */
class PhysicalTableAssetKeyTest {

  @Test
  void reproducesTheKeysDataDevelopmentAlreadyWrites() {
    assertThat(PhysicalTableAssetKey.of("1", "sales", "public", "orders"))
        .isEqualTo("table:1:sales.public.orders");
    assertThat(PhysicalTableAssetKey.of("1", "sales", "archive", "orders"))
        .isEqualTo("table:1:sales.archive.orders");
    assertThat(PhysicalTableAssetKey.of("1", "warehouse", "archive", "orders"))
        .isEqualTo("table:1:warehouse.archive.orders");
    // MySQL 把两段名解析成 db.tbl，模式位留空但分隔符照旧。
    assertThat(PhysicalTableAssetKey.of("1", "archive", "", "orders"))
        .isEqualTo("table:1:archive..orders");
    assertThat(PhysicalTableAssetKey.of("ds-a", "", "sales schema", "order-items"))
        .isEqualTo("table:ds-a:.sales schema.order-items");
  }

  @Test
  void unresolvedContextNeverAliasesAConfirmedAsset() {
    assertThat(PhysicalTableAssetKey.of("1", "", "", "orders"))
        .isEqualTo("table:unresolved:1:..orders");
    assertThat(PhysicalTableAssetKey.of("1", "", "", "orders"))
        .isNotEqualTo(PhysicalTableAssetKey.of("1", "", "public", "orders"));
  }

  @Test
  void databaseSchemaAndDatasourceAllTakePartInIdentity() {
    assertThat(PhysicalTableAssetKey.of("1", "sales", "public", "orders"))
        .isNotEqualTo(PhysicalTableAssetKey.of("1", "marketing", "public", "orders"))
        .isNotEqualTo(PhysicalTableAssetKey.of("1", "sales", "private", "orders"))
        .isNotEqualTo(PhysicalTableAssetKey.of("2", "sales", "public", "orders"));
  }

  @Test
  void neverReNormalisesWhatTheCallerPassedIn() {
    // 大小写/空白的规范化属于源域（TableIdentityResolver.normalize）；这里再动一次就是两套口径。
    assertThat(PhysicalTableAssetKey.of("DS-1", "Sales", "PUBLIC", "Orders"))
        .isEqualTo("table:DS-1:Sales.PUBLIC.Orders");
  }
}
