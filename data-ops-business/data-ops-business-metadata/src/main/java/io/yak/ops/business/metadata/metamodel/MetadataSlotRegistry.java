package io.yak.ops.business.metadata.metamodel;

import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.BaseType;
import io.yak.ops.common.enums.metadata.MetadataEnums.SlotName;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.business.metadata.exception.MetadataException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 提槽的集中登记（plan §2.4.1）。
 *
 * <p><b>为什么是共享槽位</b>：统一表混装所有类型，若每个 {@code field_def} 开一个生成列，表宽无界增长，
 * 且每次加字段都要 ALTER 全表。<b>为什么必须一次建齐</b>：实测给带数据的表
 * {@code ADD COLUMN … GENERATED … STORED} 只支持 {@code ALGORITHM=COPY}（INSTANT/INPLACE 均报 1845）
 * = 整表复制、期间不能并发 DML。所以列数封顶 7，日后加第 8 个槽是一次运维窗口而不是免费迁移。
 *
 * <p>代价：一个槽在同一时刻只能承载一种语义。所以两个类型抢同一个槽时<b>报错而不是静默复用</b>——
 * 静默复用会让索引语义悄悄漂移，比保存失败难查得多。
 */
@Component
public class MetadataSlotRegistry {

  private final MetadataTypeRegistry typeRegistry;

  public MetadataSlotRegistry(MetadataTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
  }

  /** "谁占了哪个槽"的可读视图。 */
  public List<SlotOccupancy> occupancy() {
    List<SlotOccupancy> occupancies = new ArrayList<>();
    for (var definition : typeRegistry.allTypeDefinitions()) {
      for (MdFieldDefPO field : definition.fields()) {
        if (field.getStorageSlot() != null && !field.getStorageSlot().isBlank()) {
          occupancies.add(
              new SlotOccupancy(field.getStorageSlot(), definition.typeName(), field.getFieldName()));
        }
      }
    }
    return List.copyOf(occupancies);
  }

  public List<String> freeSlots() {
    return Arrays.stream(SlotName.values())
        .map(SlotName::column)
        .filter(column -> occupancy().stream().noneMatch(usage -> usage.slot().equals(column)))
        .toList();
  }

  /**
   * 已分配槽位时的合法性校验：槽不存在 → 49011；与生成列物理型不匹配 → 49037；
   * 同槽承载了不同语义 → 49010。
   *
   * <p><b>不管"该不该提槽"</b>——{@code searchable}/{@code facetable} 是否需要落点、
   * 能不能改用固有列，是 {@link MetamodelValidationService} 的判断（它看得到全部落点）。
   *
   * @param candidate 待保存的字段定义
   */
  public void validateAssignment(String typeName, MdFieldDefPO candidate) {
    String slot = candidate.getStorageSlot();
    if (slot == null || slot.isBlank()) {
      return;
    }
    Optional<MetadataErrorCode> problem =
        conflict(SlotName.fromColumn(slot), BaseType.valueOf(candidate.getBaseType()), typeName,
            candidate.getFieldName(), occupancy());
    if (problem.isPresent()) {
      throw new MetadataException(problem.get(), "slot=" + slot);
    }
  }

  /**
   * 纯函数内核：不读库、不打 Spring，单测直接喂占用表。
   *
   * <p><b>冲突判据是"同槽 + 不同属性名"</b>，不是"同槽 + 不同类型"。生成列取的是
   * {@code md_attributes.$."<槽名>"}，所以同一个槽承载的是<b>一个属性语义</b>：
   * {@code table.tableName} 与 {@code table.databaseName} 是两个语义，各占一个槽；
   * 而 {@code table.databaseName} 与 {@code database.databaseName} 共用 s_str_2 是<b>正确</b>的
   * （同一语义、同一取值口径），若按类型互斥就等于把 7 个槽拆给 8 类实体，第一天就不够用。
   */
  public static Optional<MetadataErrorCode> conflict(
      SlotName slot, BaseType baseType, String typeName, String fieldName, List<SlotOccupancy> occupied) {
    if (slot == null) {
      return Optional.of(MetadataErrorCode.SLOT_NOT_FOUND);
    }
    if (!accepts(slot, baseType)) {
      return Optional.of(MetadataErrorCode.INVALID_ARGUMENT);
    }
    boolean blocked =
        occupied.stream()
            .anyMatch(
                usage ->
                    usage.slot().equals(slot.column()) && !usage.fieldName().equals(fieldName));
    return blocked ? Optional.of(MetadataErrorCode.SLOT_CONFLICT) : Optional.empty();
  }

  /** 生成列的物理类型决定它能装什么。 */
  public static boolean accepts(SlotName slot, BaseType baseType) {
    if (slot == null || baseType == null) {
      return false;
    }
    return switch (baseType) {
      case STRING, ENTITY_REFERENCE -> slot.name().startsWith("S_STR");
      case INTEGER -> slot == SlotName.S_NUM_1 || slot == SlotName.S_NUM_2;
      // 两个数值槽都是 BIGINT：塞小数等于静默截断，宁可不给槽。
      case NUMBER -> false;
      case BOOLEAN -> slot == SlotName.S_BOOL_1;
      case DATE, DATETIME -> slot == SlotName.S_DATE_1;
      case JSON, ARRAY -> false;
    };
  }

  public record SlotOccupancy(String slot, String typeName, String fieldName) {}
}
