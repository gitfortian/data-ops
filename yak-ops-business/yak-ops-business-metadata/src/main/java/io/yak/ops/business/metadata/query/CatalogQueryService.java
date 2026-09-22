package io.yak.ops.business.metadata.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.metamodel.MetadataFieldLocations;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.common.bean.po.metadata.MdFieldDefPO;
import io.yak.ops.core.project.CurrentProject;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 目录行的<b>按键直读</b>路径（ticket 118）：单个实体、批量取、子级、按资产键或物理坐标定位。
 *
 * <p>与统一检索的分工是<b>入参形状</b>而不是两张表：检索侧要面（facet）、游标与相关性，走
 * {@link SearchConditionBuilder} 装配的 {@link SearchPlan}；这里只有"按主键/父键/固有列取行"，
 * 一条 WHERE 就够，不值得为它编一份查询计划。两侧共用 {@link CatalogRowRead}，
 * 所以"详情里有的字段搜索里没有"这类漂移在结构上不可能发生。
 *
 * <p>每条读都带 {@code project_id} 与 {@code gone_at IS NULL}：撤销过的实体不出现在任何一块里。
 */
@Component
public class CatalogQueryService {

  /** 批量入口的硬上限：选择器一次最多取这么多，超过即截断（无界禁止，§0.11）。 */
  public static final int MAX_BATCH = 200;

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final MetadataTypeRegistry typeRegistry;
  private final CurrentProject currentProject;

  public CatalogQueryService(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper objectMapper,
      MetadataTypeRegistry typeRegistry,
      CurrentProject currentProject) {
    this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
    this.typeRegistry = typeRegistry;
    this.currentProject = currentProject;
  }

  public Optional<EntityDTO> byId(long id) {
    return first(read("a.id = :id AND a.gone_at IS NULL", scoped().addValue("id", id), null));
  }

  /** 一次 IN，不是 N 次单取；入参按 {@link #MAX_BATCH} 去重截断。 */
  public List<EntityDTO> byIds(Collection<Long> ids) {
    List<Long> capped = ids == null ? List.of() : ids.stream().filter(Objects::nonNull)
        .distinct().limit(MAX_BATCH).toList();
    if (capped.isEmpty()) {
      return List.of();
    }
    return read("a.id IN (:ids) AND a.gone_at IS NULL", scoped().addValue("ids", capped),
        " ORDER BY a.id");
  }

  public Optional<EntityDTO> byAssetKey(String assetKey) {
    return first(read("a.asset_key = :assetKey AND a.gone_at IS NULL",
        scoped().addValue("assetKey", assetKey), null));
  }

  /**
   * 子级实体。{@code childTypeName} 留空时按元模型的父子对推导（哪个类型的 {@code parent_types}
   * 含本类型），"这类实体有没有子级"是数据，不在这里写死。
   */
  public List<EntityDTO> children(long parentId, String childTypeName) {
    Long childTypeId = entityTypeId(childTypeName).orElse(null);
    if (childTypeId == null) {
      return List.of();
    }
    MapSqlParameterSource params =
        scoped().addValue("parentId", parentId).addValue("childTypeId", childTypeId);
    return read(
        "a.parent_asset_id = :parentId AND a.type_id = :childTypeId AND a.gone_at IS NULL",
        params, " ORDER BY a.id");
  }

  /** 本类型声明的子类型名；没有父子对时为空——调用方据此知道"这类实体没有子级"，不是出错。 */
  public Optional<String> childTypeOf(String typeName) {
    for (TypeDefinition definition : typeRegistry.allTypeDefinitions()) {
      if (!definition.isEntity() || definition.typeName().equals(typeName)) {
        continue;
      }
      String parents = definition.type().getParentTypes();
      if (parents != null && Arrays.stream(parents.split(","))
          .map(String::trim).anyMatch(typeName::equals)) {
        return Optional.of(definition.typeName());
      }
    }
    return Optional.empty();
  }

  /**
   * 某表的全部物理列：先定位表行，再取其列子级（两条语句，都不出新表）。
   * 表不存在或该类型没有声明子级时给空——"没有"与"还没采到"在目录里同形，由采集侧的运行历史解释。
   */
  public List<EntityDTO> physicalColumns(String datasourceId, String database, String table) {
    Optional<EntityDTO> tableRow = physicalTable(datasourceId, database, table);
    if (tableRow.isEmpty()) {
      return List.of();
    }
    return children(tableRow.get().id(), childTypeOf(tableRow.get().typeName()).orElse(null));
  }

  /**
   * 按物理坐标定位表行。<b>不替调用方拼资产键</b>（键的拼法归采集侧 {@code PhysicalTableAssetKey}，
   * 这里再拼一份就是第二个出处），直接按固有列查。
   */
  public Optional<EntityDTO> physicalTable(String datasourceId, String database, String table) {
    Long tableTypeId = entityTypeId("table").orElse(null);
    if (tableTypeId == null) {
      return Optional.empty();
    }
    MapSqlParameterSource params = scoped()
        .addValue("datasourceId", datasourceId)
        .addValue("database", database)
        .addValue("table", table)
        .addValue("tableTypeId", tableTypeId);
    return first(read(
        "a.type_id = :tableTypeId AND a.gone_at IS NULL"
            + " AND a.data_source_id = :datasourceId AND a.database_name = :database"
            + " AND a.table_name = :table",
        params, " ORDER BY a.schema_name, a.id LIMIT 1"));
  }

  private Optional<Long> entityTypeId(String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return Optional.empty();
    }
    return typeRegistry.find(typeName.trim()).filter(TypeDefinition::isEntity)
        .map(definition -> definition.type().getId());
  }

  private MapSqlParameterSource scoped() {
    return new MapSqlParameterSource().addValue("projectId", currentProject.requireProjectId());
  }

  private List<EntityDTO> read(String where, MapSqlParameterSource params, String tail) {
    String sql = "SELECT " + CatalogRowRead.SELECT_COLUMNS
        + " FROM yak_metadata_asset a WHERE a.project_id = :projectId AND " + where
        + (tail == null ? "" : tail);
    return jdbc.query(sql, params, (ResultSet rs, int rowNum) ->
        toEntityDto(CatalogRowRead.mapRow(rs, objectMapper)));
  }

  /**
   * 目录行 Map → {@link EntityDTO}。搜索侧的行同样能进这里（两边读的就是同一批列），
   * 所以 {@code MetadataQueryApi.search} 不必为了换个形状再读一遍库。
   *
   * <p>命名规则落点：{@code type_id} 不出 API 层（plan §4.2）；换不回名字只可能是元模型行被删了。
   */
  public EntityDTO toEntityDto(Map<String, Object> row) {
    Map<String, Object> facts = new LinkedHashMap<>(row);
    Object typeId = facts.remove("typeId");
    // 搜索侧的行到这里时 typeId 已被换成 typeName（它还要出 typeDisplayName），不再反查一遍。
    String typeName = facts.get("typeName") instanceof String translated && !translated.isBlank()
        ? translated
        : typeNameByTypeId().get(typeId);
    Object attributes = facts.remove("attributes");
    Map<String, Object> bag = attributes instanceof Map<?, ?> map ? castMap(map) : Map.of();
    // 属性袋里存进过非 JSON 值：原值留在 facts 里让展示面看得见，不静默换成空袋。
    if (attributes instanceof String raw && !raw.isBlank()) {
      facts.put("attributesUnparsed", raw);
    }
    return new EntityDTO(
        ((Number) facts.get("id")).longValue(),
        typeName,
        Map.copyOf(facts),
        bag,
        slotValues(typeName, bag));
  }

  /**
   * 槽值 = 属性袋里那个<b>与生成列同名</b>的键（{@link MetadataFieldLocations} 的 SLOT 分支：
   * 袋键与列同名，所以读列是多余的一趟）。翻回字段名是为了消费方不必知道哪个字段占了哪个槽。
   */
  private Map<String, Object> slotValues(String typeName, Map<String, Object> bag) {
    if (typeName == null || bag.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (MdFieldDefPO field : typeRegistry.fields(typeName)) {
      MetadataFieldLocations.FieldLocation location = MetadataFieldLocations.of(field);
      if (location.kind() == MetadataFieldLocations.Kind.SLOT
          && bag.containsKey(location.jsonKey())) {
        out.put(field.getFieldName(), bag.get(location.jsonKey()));
      }
    }
    return Map.copyOf(out);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Map<?, ?> raw) {
    return (Map<String, Object>) raw;
  }

  private Map<Long, String> typeNameByTypeId() {
    Map<Long, String> out = new LinkedHashMap<>();
    for (TypeDefinition definition : typeRegistry.allTypeDefinitions()) {
      if (definition.isEntity()) {
        out.put(definition.type().getId(), definition.typeName());
      }
    }
    return out;
  }

  private static Optional<EntityDTO> first(List<EntityDTO> rows) {
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }
}
