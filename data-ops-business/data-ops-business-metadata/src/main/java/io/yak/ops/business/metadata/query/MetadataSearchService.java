package io.yak.ops.business.metadata.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeCategory;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.core.project.CurrentProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 统一检索的应用服务：把 HTTP 层的请求翻成元模型解释过的 {@link SearchPlan}，交给接缝执行，
 * 再把 typeId 翻译回 typeName 出参（命名规则：API 层只有 typeName，落库才有 type_id，plan §4.2）。
 */
@Service
public class MetadataSearchService {

  private static final TypeReference<Map<String, Object>> FILTER_TYPE = new TypeReference<>() {};

  private final MetadataTypeRegistry typeRegistry;
  private final CurrentProject currentProject;
  private final MetadataSearchBackend backend;
  private final ObjectMapper objectMapper;

  public MetadataSearchService(
      MetadataTypeRegistry typeRegistry,
      CurrentProject currentProject,
      MetadataSearchBackend backend,
      ObjectMapper objectMapper) {
    this.typeRegistry = typeRegistry;
    this.currentProject = currentProject;
    this.backend = backend;
    this.objectMapper = objectMapper;
  }

  public record FacetView(String typeName, String displayName, long count) {}

  public record SearchResultView(
      List<Map<String, Object>> items,
      List<FacetView> typeFacets,
      long total,
      int from,
      int size,
      String nextSearchAfter,
      Map<String, Object> explanation) {}

  /** 请求参数中的 JSON 串（queryFilter/postFilter）在此解析；形状错误 = 49024。 */
  public record HttpRequest(
      String q,
      List<String> index,
      String queryFilterJson,
      String postFilterJson,
      List<String> includeFields,
      List<String> excludeFields,
      String sortField,
      String sortOrder,
      String searchAfter,
      Integer from,
      Integer size,
      boolean getHierarchy,
      boolean trackTotalHits,
      boolean explain) {}

  /**
   * 内核入参：筛选已是解析好的 Map。
   *
   * <p>有了这一层，HTTP 面与进程内消费方（{@code MetadataQueryApi.search}）走的是<b>同一条</b>
   * 计划装配路径；否则要么让 api 去拼一段 JSON 字符串，要么两套语义各写一遍——两者都会漂。
   */
  public record SearchRequest(
      String q,
      List<String> index,
      Map<String, Object> queryFilter,
      Map<String, Object> postFilter,
      List<String> includeFields,
      List<String> excludeFields,
      String sortField,
      String sortOrder,
      String searchAfter,
      int from,
      Integer size,
      boolean getHierarchy,
      boolean trackTotalHits,
      boolean explain) {}

  public SearchResultView search(HttpRequest request) {
    return search(new SearchRequest(
        request.q(), request.index(), parseFilter(request.queryFilterJson()),
        parseFilter(request.postFilterJson()), request.includeFields(), request.excludeFields(),
        request.sortField(), request.sortOrder(),
        request.searchAfter(), request.from() == null ? 0 : request.from(), request.size(),
        request.getHierarchy(), request.trackTotalHits(), request.explain()));
  }

  public SearchResultView search(SearchRequest request) {
    Long projectId = currentProject.requireProjectId();
    Map<String, Long> typeIdsByName = entityTypeIds();
    TypeSurface surface = resolveSurface(request.index(), typeIdsByName);

    SearchQuery query = new SearchQuery(
        request.q(),
        request.index() == null ? List.of() : List.copyOf(request.index()),
        request.queryFilter() == null ? Map.of() : request.queryFilter(),
        request.postFilter() == null ? Map.of() : request.postFilter(),
        request.sortField(),
        request.sortOrder(),
        request.searchAfter(),
        request.from(),
        request.size() == null ? SearchConditionBuilder.DEFAULT_SIZE : request.size(),
        request.getHierarchy());

    SearchPlan plan = new SearchConditionBuilder().build(projectId, query, surface, typeIdsByName);
    SearchResult result = backend.search(plan);
    return toView(result, plan, surface, request, typeIdsByName);
  }

  // ===========================================================================
  // 检索面解析
  // ===========================================================================

  private Map<String, Long> entityTypeIds() {
    Map<String, Long> out = new LinkedHashMap<>();
    for (TypeDefinition definition : typeRegistry.allTypeDefinitions()) {
      if (definition.isEntity()) {
        out.put(definition.typeName(), definition.type().getId());
      }
    }
    return out;
  }

  private TypeSurface resolveSurface(List<String> index, Map<String, Long> typeIdsByName) {
    List<TypeDefinition> picked = new ArrayList<>();
    if (index == null || index.isEmpty()) {
      // 默认检索面 = search_include_by_default=1 的启用实体（§4.6：tableColumn 在这里被挡下）。
      typeRegistry.activeEntityTypes().stream()
          .filter(definition -> Boolean.TRUE.equals(definition.type().getSearchIncludeByDefault()))
          .forEach(picked::add);
    } else {
      for (String typeName : index) {
        String trimmed = typeName.trim();
        TypeDefinition definition = typeRegistry.find(trimmed).orElse(null);
        if (definition == null || !definition.isEntity()) {
          throw new MetadataException(MetadataErrorCode.TYPE_NOT_FOUND, "index=" + trimmed);
        }
        if (!TypeStatus.ACTIVE.name().equals(definition.type().getStatus())) {
          throw new MetadataException(MetadataErrorCode.TYPE_DEPRECATED, "index=" + trimmed);
        }
        picked.add(definition);
      }
    }
    return new TypeSurface(picked.stream().map(MetadataSearchService::toSurfaceType).toList());
  }

  private static SurfaceType toSurfaceType(TypeDefinition definition) {
    Float weight = definition.type().getSearchDefaultWeight();
    return new SurfaceType(
        definition.type().getId(),
        definition.typeName(),
        definition.type().getDisplayName(),
        weight == null ? 1.0d : weight.doubleValue(),
        definition.fields().stream()
            .filter(field -> !Boolean.TRUE.equals(field.getDeprecated()))
            .map(field -> field.getFieldName())
            .toList(),
        definition.searchableFields().stream()
            .filter(field -> field.getStorageSlot() != null && !field.getStorageSlot().isBlank())
            .map(field -> new SlotField(
                field.getFieldName(),
                field.getStorageSlot().trim().toLowerCase(),
                field.getMatchType()))
            .toList());
  }

  private Map<String, Object> parseFilter(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> parsed = objectMapper.readValue(json, FILTER_TYPE);
      return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
    } catch (Exception e) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID,
          "queryFilter/postFilter 须为 JSON 对象: " + e.getMessage());
    }
  }

  // ===========================================================================
  // 出参装配
  // ===========================================================================

  private SearchResultView toView(
      SearchResult result,
      SearchPlan plan,
      TypeSurface surface,
      SearchRequest request,
      Map<String, Long> typeIdsByName) {

    Map<Long, SurfaceType> byId = new HashMap<>();
    for (SurfaceType type : surface.types()) {
      byId.put(type.id(), type);
    }
    Map<Long, String> nameById = new HashMap<>();
    typeIdsByName.forEach((name, id) -> nameById.put(id, name));

    Set<String> include = toSet(request.includeFields());
    Set<String> exclude = toSet(request.excludeFields());
    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, Object> row : result.rows()) {
      translateType(row, byId, nameById);
      if (result.columnHits() != null
          && row.get("id") instanceof Long id
          && "table".equals(row.get("typeName"))) {
        row.put("matchedColumnCount", result.columnHits().getOrDefault(id, 0L));
      }
      items.add(prune(row, include, exclude));
    }

    List<FacetView> facets = result.typeFacets().entrySet().stream()
        .map(entry -> {
          SurfaceType type = byId.get(entry.getKey());
          return new FacetView(
              type != null ? type.typeName() : String.valueOf(entry.getKey()),
              type != null ? type.displayName() : null,
              entry.getValue());
        })
        .sorted(Comparator.comparingLong(FacetView::count).reversed())
        .toList();
    long total = result.typeFacets().values().stream().mapToLong(Long::longValue).sum();

    Map<String, Object> explanation = null;
    if (request.explain()) {
      explanation = new LinkedHashMap<>();
      explanation.put("backend", backend.name());
      explanation.put("booleanQuery", plan.booleanQuery());
      explanation.put("degradedToLike", plan.degradedToLike());
      explanation.put("totalSource", "facet-aggregation");
      explanation.put("trackTotalHits", request.trackTotalHits());
      explanation.put("sqlCount", result.renderedSql().size());
      explanation.put("sql", result.renderedSql());
      explanation.put("notes", plan.explainNotes());
    }
    return new SearchResultView(items, facets, total, plan.offset(), plan.size(),
        result.nextSearchAfter(), explanation);
  }

  /** 命名规则落点：出参只见 typeName/typeDisplayName，type_id 不出 API 层（plan §4.2）。 */
  private static void translateType(
      Map<String, Object> row, Map<Long, SurfaceType> byId, Map<Long, String> nameById) {
    Object typeId = row.remove("typeId");
    row.put("typeName", nameById.get(typeId));
    if (typeId instanceof Long id && byId.get(id) != null) {
      row.put("typeDisplayName", byId.get(id).displayName());
    }
    if (row.get("parent") instanceof Map<?, ?> parent) {
      @SuppressWarnings("unchecked")
      Map<String, Object> parentMap = (Map<String, Object>) parent;
      Object parentTypeId = parentMap.remove("typeId");
      parentMap.put("typeName", nameById.get(parentTypeId));
    }
  }

  private static Map<String, Object> prune(
      Map<String, Object> row, Set<String> include, Set<String> exclude) {
    // 契约锚点字段永远保留：没有 id/typeName 的行下一页游标都拼不出来。
    Set<String> anchors = Set.of("id", "typeName", "assetKey");
    Map<String, Object> out = new LinkedHashMap<>();
    row.forEach((key, value) -> {
      if (!include.isEmpty() && !include.contains(key) && !anchors.contains(key)) {
        return;
      }
      if (exclude.contains(key)) {
        return;
      }
      out.put(key, value);
    });
    return out;
  }

  private static Set<String> toSet(List<String> fields) {
    if (fields == null || fields.isEmpty()) {
      return Set.of();
    }
    Set<String> out = new HashSet<>();
    for (String field : fields) {
      if (field != null && !field.isBlank()) {
        out.add(field.trim());
      }
    }
    return out;
  }
}
