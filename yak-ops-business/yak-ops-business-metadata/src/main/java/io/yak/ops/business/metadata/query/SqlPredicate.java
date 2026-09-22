package io.yak.ops.business.metadata.query;

import java.util.List;
import java.util.Map;

/**
 * 一个已渲染的 SQL 谓词与其具名参数。
 *
 * <p>不变式：{@code fragment} 里出现的占位符全部是 {@code :name} 具名参数，且只来自
 * {@code MetadataNativeFilterColumns} / 槽位列白名单这两处字面量——用户输入永远是绑定值，
 * 从不拼进 SQL 文本。
 */
public record SqlPredicate(String fragment, Map<String, Object> params) {

  public static SqlPredicate of(String fragment, String param, Object value) {
    return new SqlPredicate(fragment, Map.of(param, value));
  }
}

/** 检索面上的一个实体类型，连同它在搜索侧需要的最小元模型信息（builder 保持纯函数，不碰 Spring）。 */
record SurfaceType(
    Long id,
    String typeName,
    String displayName,
    double weight,
    List<String> declaredFields,
    List<SlotField> searchableSlots) {}

/** field_def 声明的一个可搜槽位：列名来自白名单，match_type 决定条件形状（plan §4.5 四条表）。 */
record SlotField(String fieldName, String slotColumn, String matchType) {}

/** 检索面快照：由 MetadataSearchService 从 MetadataTypeRegistry 解析出来喂给 builder。 */
record TypeSurface(List<SurfaceType> types) {

  boolean isEmpty() {
    return types.isEmpty();
  }

  SurfaceType byName(String typeName) {
    return types.stream().filter(t -> t.typeName().equals(typeName)).findFirst().orElse(null);
  }
}
