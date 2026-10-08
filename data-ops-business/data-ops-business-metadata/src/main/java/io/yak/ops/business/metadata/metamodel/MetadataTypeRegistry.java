package io.yak.ops.business.metadata.metamodel;

import io.yak.ops.business.metadata.dao.mapper.MdFieldDefMapper;
import io.yak.ops.business.metadata.dao.mapper.MdTypeDefMapper;
import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.MatchType;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeCategory;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.business.metadata.exception.MetadataException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 元模型的读侧：类型与字段定义的解析与缓存。
 *
 * <p>缓存<b>必须可失效</b>——"插一行 field_def 后不重启即生效"是"加字段免改表"的硬证明（plan §8 P0b）。
 * 本模块的写接口会主动 {@link #invalidate()}；直接改库的场景由 TTL 兜住。
 */
@Component
public class MetadataTypeRegistry {

  private final MdTypeDefMapper typeMapper;
  private final MdFieldDefMapper fieldMapper;
  private final long ttlMillis;

  private volatile Snapshot snapshot = Snapshot.empty();

  public MetadataTypeRegistry(
      MdTypeDefMapper typeMapper,
      MdFieldDefMapper fieldMapper,
      @Value("${yak.metadata.type-cache-ttl-ms:30000}") long ttlMillis) {
    this.typeMapper = typeMapper;
    this.fieldMapper = fieldMapper;
    this.ttlMillis = ttlMillis;
  }

  public Optional<TypeDefinition> find(String typeName) {
    return Optional.ofNullable(current().byName.get(normalize(typeName)));
  }

  public TypeDefinition require(String typeName) {
    return find(typeName)
        .orElseThrow(() -> new MetadataException(MetadataErrorCode.TYPE_NOT_FOUND, "type=" + typeName));
  }

  /** 已启用的实体类型（目录/搜索/前端渲染的驱动源）。 */
  public List<TypeDefinition> activeEntityTypes() {
    return current().byName.values().stream()
        .filter(definition -> TypeCategory.ENTITY.name().equals(definition.type().getCategory()))
        .filter(definition -> TypeStatus.ACTIVE.name().equals(definition.type().getStatus()))
        .sorted(Comparator.comparing(definition -> definition.type().getTypeName()))
        .toList();
  }

  /** 全部类型定义（含未启用与字段类型）——槽位占用统计要看全量，不能只看 ACTIVE 实体。 */
  public List<TypeDefinition> allTypeDefinitions() {
    return current().byName.values().stream()
        .sorted(Comparator.comparing(definition -> definition.type().getTypeName()))
        .toList();
  }

  /** 某类型按 ordinal 排过序的全部字段定义。 */
  public List<MdFieldDefPO> fields(String typeName) {
    return require(typeName).fields();
  }

  /** 登记属性时的合法键集合；不在其中的属性直接拒（plan §9 T18）。 */
  public boolean isDefinedField(String typeName, String fieldName) {
    return find(typeName).flatMap(definition -> definition.field(fieldName)).isPresent();
  }

  /** 失效缓存：元模型写操作之后必须调用。 */
  public void invalidate() {
    snapshot = Snapshot.empty();
  }

  private Snapshot current() {
    Snapshot local = snapshot;
    long now = System.nanoTime() / 1_000_000L;
    if (!local.isEmpty() && now - local.loadedAtMillis < ttlMillis) {
      return local;
    }
    synchronized (this) {
      local = snapshot;
      if (!local.isEmpty() && System.nanoTime() / 1_000_000L - local.loadedAtMillis < ttlMillis) {
        return local;
      }
      Snapshot reloaded = load();
      snapshot = reloaded;
      return reloaded;
    }
  }

  private Snapshot load() {
    List<MdTypeDefPO> types = typeMapper.selectList(null);
    List<MdFieldDefPO> fields = fieldMapper.selectList(null);
    // Index fields once; scanning the complete list for every type scales with types * fields.
    Map<Long, List<MdFieldDefPO>> fieldsByTypeId = new HashMap<>();
    for (MdFieldDefPO field : fields) {
      fieldsByTypeId.computeIfAbsent(field.getTypeId(), unused -> new ArrayList<>()).add(field);
    }
    Map<String, TypeDefinition> byName = new java.util.LinkedHashMap<>();
    for (MdTypeDefPO type : types) {
      List<MdFieldDefPO> own = fieldsByTypeId.getOrDefault(type.getId(), List.of()).stream()
          .sorted(Comparator.comparing(MdFieldDefPO::getOrdinal))
          .toList();
      byName.put(type.getTypeName(), new TypeDefinition(type, own));
    }
    return new Snapshot(Map.copyOf(byName), System.nanoTime() / 1_000_000L);
  }

  private static String normalize(String typeName) {
    return typeName == null ? null : typeName.trim();
  }

  /** 一个实体/字段类型连同它的字段定义。 */
  public record TypeDefinition(MdTypeDefPO type, List<MdFieldDefPO> fields) {

    public String typeName() {
      return type.getTypeName();
    }

    public boolean isEntity() {
      return TypeCategory.ENTITY.name().equals(type.getCategory());
    }

    public boolean isCollectible() {
      return Boolean.TRUE.equals(type.getCollectible());
    }

    public Optional<MdFieldDefPO> field(String fieldName) {
      return fields.stream().filter(field -> field.getFieldName().equals(fieldName)).findFirst();
    }

    /** 参与 {@code q} 全文检索的字段。 */
    public List<MdFieldDefPO> searchableFields() {
      return fields.stream()
          .filter(field -> Boolean.TRUE.equals(field.getSearchable()))
          .filter(field -> !Boolean.TRUE.equals(field.getDeprecated()))
          .toList();
    }

    /** 进筛选聚合面板的字段。 */
    public List<MdFieldDefPO> facetableFields() {
      return fields.stream().filter(field -> Boolean.TRUE.equals(field.getFacetable())).toList();
    }

    public boolean matchesText(MdFieldDefPO field) {
      return MatchType.TEXT.value().equalsIgnoreCase(field.getMatchType());
    }
  }

  private record Snapshot(Map<String, TypeDefinition> byName, long loadedAtMillis) {

    static Snapshot empty() {
      return new Snapshot(Map.of(), 0L);
    }

    boolean isEmpty() {
      return byName.isEmpty();
    }
  }
}
