package io.yak.ops.business.metadata.harvest;

import static io.yak.ops.business.metadata.harvest.HarvestFixtures.harvestTypeRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.common.bean.po.metadata.MdFieldDefPO;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code md_attributes} 的编码规则（ticket 114，plan §4.5 / §9 T18）。
 *
 * <p>本类锁的四件事全是<b>写进去不报错、读出来才坏</b>的那一类：键写歪→筛选恒空，
 * 日期写成 JSON 数字→整条 INSERT 报 3156，未登记属性静默进袋→半年后无人认领，
 * 键序随调用方→每轮都判成 CHANGED。四种都没有编译期痕迹，所以只能在这里钉住。
 */
class MetadataAttributeCodecTest {

  private final MetadataAttributeCodec codec = HarvestFixtures.codec();
  private final MetadataTypeRegistry registry = harvestTypeRegistry();

  @Test
  void slottedFieldsAreStoredUnderTheSlotNameNotTheFieldName() {
    // 生成列取的是 $."<槽名>"。写字段名不会报错，只会让 s_str_2 永远是 NULL、按库名筛选永远 0 行。
    String json =
        codec.encode(
            registry.require("table"),
            Map.of("databaseName", "shop", "tableName", "orders", "columnCount", 3));

    assertThat(json)
        .isEqualTo("{\"s_str_2\":\"shop\",\"tableName\":\"orders\",\"s_num_1\":3}");
  }

  @Test
  void unregisteredAttributesAreRejectedAtTheDoor() {
    assertThatThrownBy(
            () ->
                codec.encode(
                    registry.require("table"),
                    Map.of("databaseName", "shop", "engine", "InnoDB")))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.ATTRIBUTE_NOT_DEFINED);
  }

  @Test
  void datetimesLeaveAsTextBecauseTheGeneratedColumnCastsThem() {
    // 本机 8.0.46 实测：s_date_1 收到 JSON 数字直接 3156 Invalid JSON value for CAST to DATETIME。
    String json =
        codec.encode(
            registry.require("table"),
            Map.of("lastDdlTime", LocalDateTime.of(2026, 1, 2, 3, 4, 5)));

    assertThat(json).isEqualTo("{\"s_date_1\":\"2026-01-02 03:04:05\"}");
  }

  @Test
  void subSecondTimestampsKeepTheirFractionInsteadOfTruncating() {
    String json =
        codec.encode(
            registry.require("table"),
            Map.of("lastDdlTime", LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123_456_000)));

    assertThat(json).isEqualTo("{\"s_date_1\":\"2026-01-02 03:04:05.123456\"}");
  }

  @Test
  void anOversizedSlottedStringFailsLoudlyRatherThanAtInsertTime() throws Exception {
    String tooLong = "x".repeat(257);
    assertThatThrownBy(
            () -> codec.encode(registry.require("tableColumn"), Map.of("dataType", tooLong)))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.INVALID_ARGUMENT);

    // 同一长度留在袋里是合法的：槽 256 字节，袋是 JSON。别把两回事一起禁掉。
    MdFieldDefPO unslotted = HarvestFixtures.field(104L, "columnComment", "STRING", null, 80);
    TypeDefinition definition =
        new TypeDefinition(HarvestFixtures.entityType(104L, "tableColumn", "column:", "{columnName}", "COLUMN"), List.of(unslotted));
    assertThatCode(() -> codec.encode(definition, Map.of("columnComment", tooLong)))
        .doesNotThrowAnyException();
  }

  @Test
  void bagIsByteStableSoIdempotentRoundsDoNotLookLikeChanges() {
    // upsert 里 md_attributes <=> VALUES(...) 是"重跑零写入"的判据；键序随 HashMap 就永远不相等。
    Map<String, Object> asSqlOrder = new LinkedHashMap<>();
    asSqlOrder.put("databaseName", "shop");
    asSqlOrder.put("tableName", "orders");
    asSqlOrder.put("tableComment", "订单表");
    asSqlOrder.put("columnCount", 3);

    Map<String, Object> reversed = new LinkedHashMap<>();
    reversed.put("columnCount", 3);
    reversed.put("tableComment", "订单表");
    reversed.put("tableName", "orders");
    reversed.put("databaseName", "shop");

    TypeDefinition table = registry.require("table");
    String first = codec.encode(table, asSqlOrder);
    String second = codec.encode(table, reversed);

    assertThat(second).isEqualTo(first);
    assertThat(first)
        .isEqualTo(
            "{\"s_str_2\":\"shop\",\"tableName\":\"orders\",\"tableComment\":\"订单表\",\"s_num_1\":3}");
    // 打乱顺序的 HashMap 也必须给出同一串（调用方不可能都记得用 LinkedHashMap）。
    assertThat(codec.encode(table, new HashMap<>(asSqlOrder))).isEqualTo(first);
  }

  @Test
  void valuesThatAlreadyOwnARealColumnNeverEnterTheBag() {
    // layer_code/tier_label/… 在目录上有真列；再进袋就是第二份没人读、且会与列漂移的死数据。
    TypeDefinition definition =
        new TypeDefinition(
            HarvestFixtures.entityType(200L, "dataModel", "modeling:model:", "{modelCode}", "TABLE"),
            List.of(
                HarvestFixtures.field(200L, "modelCode", "STRING", null, 10),
                HarvestFixtures.field(200L, "layerCode", "STRING", null, 20),
                HarvestFixtures.field(200L, "entityStatus", "STRING", null, 30),
                HarvestFixtures.field(200L, "ownerUser", "STRING", null, 40),
                HarvestFixtures.field(200L, "domainIds", "STRING", null, 50),
                HarvestFixtures.field(200L, "tierLabel", "STRING", null, 60)));

    String json =
        codec.encode(
            definition,
            Map.of(
                "modelCode", "dwd_order",
                "layerCode", "DWD",
                "entityStatus", "PUBLISHED",
                "ownerUser", "lucas",
                "domainIds", "1,2",
                "tierLabel", "gold"));

    assertThat(json).isEqualTo("{\"modelCode\":\"dwd_order\"}");
  }

  @Test
  void blanksAndNullsAreOmittedSoTheBagHoldsOnlyWhatIsKnown() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("databaseName", "   ");
    values.put("tableName", null);
    values.put("tableType", "TABLE");
    values.put("columnCount", 0);

    // 0 是"确实有零列"，与"不知道"是两回事——INTEGER 的 0 必须留下。
    assertThat(codec.encode(registry.require("table"), values))
        .isEqualTo("{\"tableType\":\"TABLE\",\"s_num_1\":0}");
  }

  @Test
  void structuredTypesAreRoutedToTheExtensionTableNotTheBag() {
    TypeDefinition definition =
        new TypeDefinition(
            HarvestFixtures.entityType(210L, "withJson", "x:", "{code}", "TABLE"),
            List.of(
                HarvestFixtures.field(210L, "code", "STRING", null, 10),
                HarvestFixtures.field(210L, "profile", "JSON", null, 20)));

    assertThatThrownBy(
            () -> codec.encode(definition, Map.of("code", "a", "profile", Map.of("k", "v"))))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.INVALID_ARGUMENT);
  }
}
