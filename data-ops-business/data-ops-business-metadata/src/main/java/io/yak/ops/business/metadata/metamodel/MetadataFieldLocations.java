package io.yak.ops.business.metadata.metamodel;

import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import java.util.Map;
import java.util.Optional;

/**
 * 一个字段的值**存在哪里**——写入侧（属性袋 → 列）与查询侧（筛选 → 列）必须用同一份判据，
 * 否则会出现"写进袋里、按槽查"这种永远查不到的错配。
 *
 * <p>三态（plan §4.5）：
 * <ol>
 *   <li>{@link Kind#SLOT}：{@code field_def.storage_slot} 指定的生成列。热字段走这条，
 *       代价是要在 7 个槽里占一个（§2.4.1）。</li>
 *   <li>{@link Kind#NATIVE}：目录**固有列**。{@code layer_code}/{@code tier_label}/{@code entity_status}
 *       这类每类实体都有的治理维度已在 {@code yak_metadata_asset} 上有真列，
 *       不需要也不能再占槽（占了就是把 7 个槽分给一个字段语义）。</li>
 *   <li>{@link Kind#JSON_BAG}：只在 {@code md_attributes} 里 → 可展示、**不可筛**。
 *       所以 {@code searchable}/{@code facetable} 为真却没有落点的定义，保存时必须被拒。</li>
 * </ol>
 *
 * <p>{@code q} 的默认面（{@code name}/{@code display_name}/{@code summary} 上的 ngram FULLTEXT）
 * 对每类实体无条件生效，**不经本表**——所以"表名""列名"这类本身就是 {@code name} 列的字段
 * 不需要标 {@code searchable}，标了只会重复计分。
 */
public final class MetadataFieldLocations {

  private static final Map<String, String> NATIVE_COLUMNS =
      Map.of(
          "layerCode", "layer_code",
          "tierLabel", "tier_label",
          "entityStatus", "entity_status",
          "ownerUser", "owner_user",
          "domainIds", "domain_ids");

  private MetadataFieldLocations() {}

  public static FieldLocation of(MdFieldDefPO field) {
    String slot = field.getStorageSlot();
    if (slot != null && !slot.isBlank()) {
      // 袋键与生成列同名：生成列取的是 $."<槽名>"，写字段名会让槽永远 NULL（plan §2.3 第 ⑦ 段）。
      return new FieldLocation(Kind.SLOT, slot.trim(), slot.trim());
    }
    String nativeColumn = NATIVE_COLUMNS.get(field.getFieldName());
    if (nativeColumn != null) {
      return new FieldLocation(Kind.NATIVE, nativeColumn, field.getFieldName());
    }
    return new FieldLocation(Kind.JSON_BAG, null, field.getFieldName());
  }

  public static Optional<String> nativeColumn(String fieldName) {
    return Optional.ofNullable(NATIVE_COLUMNS.get(fieldName));
  }

  public enum Kind {
    SLOT,
    NATIVE,
    JSON_BAG
  }

  /**
   * @param column 可筛时用于 SQL 的列名；{@link Kind#JSON_BAG} 时为 null
   * @param jsonKey 写入 {@code md_attributes} 时用的键：**提槽字段用槽名**（生成列取的就是
   *     {@code $."<槽名>"}，plan §2.3 第 ⑦ 段），未提槽的用字段名。两边都从这里取，键才不会写歪。
   */
  public record FieldLocation(Kind kind, String column, String jsonKey) {

    public boolean filterable() {
      return kind != Kind.JSON_BAG;
    }
  }
}
