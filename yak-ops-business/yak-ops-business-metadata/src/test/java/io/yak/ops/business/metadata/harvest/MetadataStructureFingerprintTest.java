package io.yak.ops.business.metadata.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.metadata.metamodel.MetadataKeyCodec;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 结构指纹的规范化规则（plan §3.3 / ticket 114 的"指纹字段清单写成常量集合、由单测锁定"）。
 *
 * <p>这里锁的不是"算得出一个 hash"，而是<b>什么会、什么不会</b>让一轮采集被判成 CHANGED：
 * 大小写、注释空白、列的返回顺序都不该惊动下游；类型/可空/主键/位数少一个都不算同一张表。
 */
class MetadataStructureFingerprintTest {

  private static final DataSourceTable ORDERS_TABLE = table("shop", "orders", "TABLE", "订单主表");

  @Test
  void caseAndCommentLayoutNeverChangeTheFingerprint() {
    DataSourceColumn upper = column("Amount_CNY", "DECIMAL", 93, 18, 2, true, 3, false, "订单  金额\n含税费");
    DataSourceColumn lower = column("amount_cny", "decimal", 93, 18, 2, true, 3, false, "订单 金额 含税费");

    assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(upper)))
        .isEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(lower)));
  }

  @Test
  void columnReturnOrderIsIrrelevantButOrdinalIsMeaning() {
    DataSourceColumn id = column("id", "bigint", -5, 19, null, false, 1, true, "主键");
    DataSourceColumn amount = column("amount", "decimal", 93, 18, 2, true, 2, false, "金额");

    assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(id, amount)))
        .isEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(amount, id)));

    DataSourceColumn idLast = column("id", "bigint", -5, 19, null, false, 3, true, "主键");
    DataSourceColumn amountFirst = column("amount", "decimal", 93, 18, 2, true, 1, false, "金额");
    assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(id, amount)))
        .isNotEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(idLast, amountFirst)));
  }

  @Test
  void missingOrdinalsFallBackToNameOrder() {
    DataSourceColumn id = column("id", "bigint", -5, 19, null, false, 0, true, "");
    DataSourceColumn amount = column("amount", "decimal", 93, 18, 2, true, 0, false, "");
    DataSourceColumn note = column("note", "varchar", 12, 64, null, true, 0, false, "");

    assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(note, id, amount)))
        .isEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(amount, note, id)));
    assertThat(MetadataStructureFingerprint.sortedColumns(List.of(note, id, amount)))
        .extracting(DataSourceColumn::getName)
        .containsExactly("amount", "id", "note");
  }

  @Test
  void identityStaysOutOfStructureSoIdenticalShapesShareAHash() {
    DataSourceTable other = table("warehouse", "order_items", "TABLE", "订单主表");
    List<DataSourceColumn> columns = List.of(column("id", "bigint", -5, 19, null, false, 1, true, ""));

    assertThat(MetadataStructureFingerprint.contentHash(other, columns))
        .isEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, columns));
  }

  @Test
  void everyStructureAttributeIsPartOfTheFingerprint() {
    DataSourceColumn base = column("amount", "decimal", 93, 18, 2, true, 1, false, "金额");
    List<DataSourceColumn> variants =
        List.of(
            column("amount", "decimal", 93, 18, 2, true, 1, false, "金额变了"),
            column("amount_cny", "decimal", 93, 18, 2, true, 1, false, "金额"),
            column("amount", "numeric", 93, 18, 2, true, 1, false, "金额"),
            column("amount", "decimal", 2, 18, 2, true, 1, false, "金额"),
            column("amount", "decimal", 93, 17, 2, true, 1, false, "金额"),
            column("amount", "decimal", 93, 18, 3, true, 1, false, "金额"),
            column("amount", "decimal", 93, 18, 2, false, 1, false, "金额"),
            column("amount", "decimal", 93, 18, 2, true, 2, false, "金额"),
            column("amount", "decimal", 93, 18, 2, true, 1, true, "金额"));

    for (DataSourceColumn variant : variants) {
      assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(variant)))
          .as("列 %s 与基准列结构不同", variant.getName())
          .isNotEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(base)));
    }

    assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of()))
        .isNotEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(base)));
    assertThat(
            MetadataStructureFingerprint.contentHash(
                table("shop", "orders", "VIEW", "订单主表"), List.of(base)))
        .isNotEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(base)));
  }

  @Test
  void unknownNumericMetadataCollapsesToOneSpelling() {
    // 驱动报 null 还是报 -1 都是"没有精度"，不该被看成一次结构变更。
    DataSourceColumn unknown = column("amount", "decimal", 93, null, null, true, 1, false, "");
    DataSourceColumn minusOne = column("amount", "decimal", 93, -1, -1, true, 1, false, "");

    assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(unknown)))
        .isEqualTo(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(minusOne)));
  }

  @Test
  void fingerprintFieldSetsCoverTheSpiExactly() {
    assertThat(fieldNames(DataSourceColumn.class))
        .isEqualTo(MetadataStructureFingerprint.COLUMN_FIELDS);
    assertThat(fieldNames(DataSourceTable.class))
        .isEqualTo(Set.of("database", "schema", "name", "type", "remarks"));
    // 表级只取 type + 注释：库名/表名/模式名归 asset_key 与 FQN。
    assertThat(MetadataStructureFingerprint.TABLE_FIELDS).isEqualTo(Set.of("type", "remarks"));
  }

  @Test
  void volatileFactsAreNeverFingerprinted() {
    assertThat(MetadataStructureFingerprint.COLUMN_FIELDS)
        .doesNotContainAnyElementsOf(MetadataStructureFingerprint.VOLATILE_FIELDS);
    assertThat(MetadataStructureFingerprint.TABLE_FIELDS)
        .doesNotContainAnyElementsOf(MetadataStructureFingerprint.VOLATILE_FIELDS);
  }

  @Test
  void contentHashIsTheCanonicalStringsDigest() {
    DataSourceColumn column = column("id", "bigint", -5, 19, null, false, 1, true, "主键");
    String canonical = MetadataStructureFingerprint.canonicalTableString(ORDERS_TABLE, List.of(column));

    assertThat(canonical).isEqualTo("TABLE|订单主表|id:bigint:-5:19:-1:0:1:1:主键");
    assertThat(MetadataStructureFingerprint.contentHash(ORDERS_TABLE, List.of(column)))
        .isEqualTo(MetadataKeyCodec.digestHex(canonical))
        .hasSize(32)
        .matches("^[0-9a-f]{32}$");
  }

  private static Set<String> fieldNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .filter(field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()))
        .map(Field::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static DataSourceTable table(String database, String name, String type, String remarks) {
    return new DataSourceTable(database, null, name, type, remarks);
  }

  private static DataSourceColumn column(
      String name,
      String typeName,
      int jdbcType,
      Integer size,
      Integer scale,
      boolean nullable,
      int ordinal,
      boolean primaryKey,
      String remarks) {
    return new DataSourceColumn(
        name, typeName, jdbcType, size, scale, nullable, ordinal, primaryKey, remarks);
  }
}
