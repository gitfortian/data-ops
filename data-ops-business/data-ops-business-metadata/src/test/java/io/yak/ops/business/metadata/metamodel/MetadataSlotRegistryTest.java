package io.yak.ops.business.metadata.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.common.bean.po.metadata.MdFieldDefPO;
import io.yak.ops.common.bean.po.metadata.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.BaseType;
import io.yak.ops.common.enums.metadata.MetadataEnums.SlotName;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.business.metadata.metamodel.MetadataSlotRegistry.SlotOccupancy;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 槽位登记（plan §10 测试 8、§2.4.1）。 */
class MetadataSlotRegistryTest {

  private final MetadataTypeRegistry registry = MetamodelFixtures.registry(List.of(), List.of(), 0L);
  private final MetadataSlotRegistry slots = new MetadataSlotRegistry(registry);

  @Test
  void sameSlotForDifferentSemanticsIsRejectedNotSilentlyReused() {
    List<SlotOccupancy> occupied = List.of(new SlotOccupancy("s_str_1", "table", "tableName"));

    assertThat(
            MetadataSlotRegistry.conflict(
                SlotName.S_STR_1, BaseType.STRING, "table", "databaseName", occupied))
        .as("同槽承载两个不同属性 = 生成列语义静默漂移，必须报错")
        .contains(MetadataErrorCode.SLOT_CONFLICT);
  }

  @Test
  void sameSlotForTheSameAttributeAcrossTypesIsThePointNotAConflict() {
    List<SlotOccupancy> occupied = List.of(new SlotOccupancy("s_str_1", "table", "dataType"));

    // 7 个槽装 8 类实体，靠的就是"一个槽 = 一个属性语义"；按类型互斥等于第一天就不够用。
    assertThat(
            MetadataSlotRegistry.conflict(
                SlotName.S_STR_1, BaseType.STRING, "tableColumn", "dataType", occupied))
        .isEmpty();
  }

  @Test
  void unknownSlotAndTypeMismatchAreBothRejected() {
    assertThat(
            MetadataSlotRegistry.conflict(
                null, BaseType.STRING, "table", "x", List.of()))
        .contains(MetadataErrorCode.SLOT_NOT_FOUND);
    assertThat(
            MetadataSlotRegistry.conflict(
                SlotName.S_BOOL_1, BaseType.STRING, "table", "x", List.of()))
        .contains(MetadataErrorCode.INVALID_ARGUMENT);
    assertThat(
            MetadataSlotRegistry.conflict(
                SlotName.S_NUM_1, BaseType.DATE, "table", "x", List.of()))
        .contains(MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void numberDoesNotFitABigintSlot() {
    // 塞小数进 BIGINT 槽是静默截断：宁可不给槽，让人去查为什么。
    assertThat(MetadataSlotRegistry.accepts(SlotName.S_NUM_1, BaseType.NUMBER)).isFalse();
    assertThat(MetadataSlotRegistry.accepts(SlotName.S_NUM_1, BaseType.INTEGER)).isTrue();
    assertThat(MetadataSlotRegistry.accepts(SlotName.S_DATE_1, BaseType.DATETIME)).isTrue();
    assertThat(MetadataSlotRegistry.accepts(SlotName.S_STR_1, BaseType.ENTITY_REFERENCE)).isTrue();
    assertThat(MetadataSlotRegistry.accepts(SlotName.S_STR_1, BaseType.JSON)).isFalse();
  }

  @Test
  void occupancyAndFreeSlotsReadTheMetamodel() {
    MdTypeDefPO table = MetamodelFixtures.entityType(1L, "table");
    MdFieldDefPO taken = MetamodelFixtures.field(1L, "databaseName", "STRING", "s_str_2");
    MetadataTypeRegistry populated =
        MetamodelFixtures.registry(List.of(table), List.of(taken), 0L);
    MetadataSlotRegistry underTest = new MetadataSlotRegistry(populated);

    assertThat(underTest.occupancy())
        .containsExactly(new SlotOccupancy("s_str_2", "table", "databaseName"));
    assertThat(underTest.freeSlots())
        .containsExactly("s_str_1", "s_str_3", "s_num_1", "s_num_2", "s_bool_1", "s_date_1");
  }

  @Test
  void assigningATakenSlotFailsThroughTheBeanToo() {
    MdTypeDefPO table = MetamodelFixtures.entityType(1L, "table");
    MetadataTypeRegistry populated =
        MetamodelFixtures.registry(
            List.of(table),
            List.of(MetamodelFixtures.field(1L, "tableName", "STRING", "s_str_1")),
            0L);
    MetadataSlotRegistry underTest = new MetadataSlotRegistry(populated);

    MdFieldDefPO candidate = MetamodelFixtures.field(1L, "columnComment", "STRING", "s_str_1");
    assertThatThrownBy(() -> underTest.validateAssignment("table", candidate))
        .isInstanceOfSatisfying(
            MetadataException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MetadataErrorCode.SLOT_CONFLICT));
  }

  @Test
  void blankSlotIsNotThisRegistrysProblem() {
    // "该不该提槽"归校验服务判（它看得到固有列这条退路），这里只判已分配槽的合法性。
    slots.validateAssignment("table", MetamodelFixtures.field(1L, "comment", "STRING", null));
  }
}
