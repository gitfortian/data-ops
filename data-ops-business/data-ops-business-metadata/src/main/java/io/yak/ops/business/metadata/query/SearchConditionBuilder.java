package io.yak.ops.business.metadata.query;

import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.common.enums.metadata.MetadataEntityStatus;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 搜索计划的编译处：{@code q}/筛选/排序/游标 → {@link SearchPlan}。
 *
 * <p>本类是<b>零类型分支</b>的最后一道闸（plan §4.5）：attr 条件的形状只由 field_def 的
 * {@code match_type} 与槽位决定，代码里不出现 {@code if (typeName.equals("table"))} 这类东西。
 * 出现即说明"加类型免改代码"被实现层出卖了。唯一的例外是 {@code tableColumn} 的"命中 N 列"
 * 回账（§4.6）——那是检索面策略不是类型逻辑，写死在 {@link #resolveColumnRollup} 一处。
 */
public final class SearchConditionBuilder {

  static final int MAX_SIZE = 200;
  static final int DEFAULT_SIZE = 20;
  static final int MAX_OFFSET = 5000;
  private static final Set<String> PROVIDER_TYPES = Set.of("HARVESTED", "REGISTERED");
  private static final String LIKE_NOTE = "q 短于一个 n-gram，已降级 LIKE（单字查询在 ngram 索引下必命不中，plan §4.3）";

  /** sortField 白名单（plan §4.2 的"防注入"就在这张表上）；relevance 单独特判。 */
  private static final Map<String, String> SORT_COLUMNS = Map.of(
      "updateTime", "a.update_time",
      "createTime", "a.create_time",
      "name", "a.name",
      "id", "a.id");

  private int paramSeq;

  /**
   * @param typeIdsByName 全量类型名 → id（含不在检索面上的），供 queryFilter.typeName 解析；
   *                      缺失即 49002，绝不静默放过一个筛不中的类型名
   */
  public SearchPlan build(
      Long projectId,
      SearchQuery query,
      TypeSurface surface,
      Map<String, Long> typeIdsByName) {
    if (surface.isEmpty()) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID,
          "检索面为空：index 参数没有命中任何已启用类型");
    }
    List<String> notes = new ArrayList<>();
    Map<String, Object> extraParams = new LinkedHashMap<>();
    extraParams.put("projectId", projectId);

    // ---- 基础谓词（永远参与，包括聚合） --------------------------------------
    List<SqlPredicate> base = new ArrayList<>();
    base.add(new SqlPredicate("a.project_id = :projectId", Map.of()));
    // 在场性：目录搜索只看 gone_at IS NULL 的行；软删历史经详情/历史通道出，不混进检索。
    base.add(new SqlPredicate("a.gone_at IS NULL", Map.of()));
    List<Long> surfaceIds = surface.types().stream().map(SurfaceType::id).toList();
    base.add(new SqlPredicate("a.type_id IN (:surfaceIds)", Map.of("surfaceIds", surfaceIds)));

    // ---- queryFilter / postFilter -------------------------------------------
    List<SqlPredicate> queryFilters =
        compileFilters(query.queryFilter(), surface, typeIdsByName, "qf");
    List<SqlPredicate> postFilters =
        compileFilters(query.postFilter(), surface, typeIdsByName, "pf");

    // ---- q -------------------------------------------------------------------
    SqlPredicate text = compileText(query.q(), surface, notes);
    boolean degradedToLike = text != null && notes.contains(LIKE_NOTE);
    String booleanQuery = text == null ? null : String.valueOf(text.params().get("qFt"));

    // ---- 排序 / 游标 -----------------------------------------------------------
    boolean hasQ = text != null;
    String sortField = resolveSortField(query.sortField(), hasQ);
    boolean ascending = "asc".equalsIgnoreCase(query.sortOrder());
    String sortExpr;
    if ("relevance".equals(sortField)) {
      sortExpr = buildWeightCase(surface, extraParams);
    } else {
      sortExpr = SORT_COLUMNS.get(sortField);
    }
    String dir = ascending ? "ASC" : "DESC";
    String orderBySql = "relevance".equals(sortField)
        ? sortExpr + " DESC, a.update_time DESC, a.id DESC"
        : sortExpr + " " + dir + ", a.id " + dir;

    SqlPredicate cursor = null;
    if (query.searchAfter() != null && !query.searchAfter().isBlank()) {
      if ("relevance".equals(sortField)) {
        throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT,
            "searchAfter 游标要求单列排序（updateTime/createTime/name/id），relevance 不提供游标");
      }
      cursor = compileCursor(query.searchAfter(), sortExpr, ascending);
    }

    // ---- 分页预算（无界禁止，plan §0.11） --------------------------------------
    int size = clampSize(query.size(), notes);
    int offset = 0;
    if (cursor == null) {
      if (query.from() > MAX_OFFSET) {
        throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT,
            "from 超过 " + MAX_OFFSET + "：深翻请改用 searchAfter 游标");
      }
      offset = Math.max(0, query.from());
    } else if (query.from() > 0) {
      notes.add("searchAfter 存在时 from 被忽略");
    }

    // ---- 三套谓词装配（本票最重要的一条区分） ----------------------------------
    List<SqlPredicate> facetPredicates = new ArrayList<>(base);
    facetPredicates.addAll(queryFilters);
    if (text != null) {
      facetPredicates.add(text);
    }
    List<SqlPredicate> rowPredicates = new ArrayList<>(facetPredicates);
    rowPredicates.addAll(postFilters);
    if (cursor != null) {
      rowPredicates.add(cursor);
    }
    // 列命中聚合要"跨面"：index 收窄不适用于它（默认面本来就不含列，命中数才要单独还回来）。
    List<SqlPredicate> rollupPredicates = new ArrayList<>();
    rollupPredicates.add(base.get(0));
    rollupPredicates.add(base.get(1));
    rollupPredicates.addAll(queryFilters);
    if (text != null) {
      rollupPredicates.add(text);
    }
    Long columnRollupTypeId = resolveColumnRollup(surface, typeIdsByName);
    if (columnRollupTypeId != null) {
      extraParams.put("rollupTypeId", columnRollupTypeId);
    }

    return new SearchPlan(
        projectId,
        Map.copyOf(extraParams),
        List.copyOf(rowPredicates),
        List.copyOf(facetPredicates),
        List.copyOf(rollupPredicates),
        columnRollupTypeId,
        query.getHierarchy(),
        orderBySql,
        "relevance".equals(sortField) ? null : sortExpr,
        cursor,
        degradedToLike,
        booleanQuery,
        size,
        offset,
        List.copyOf(notes));
  }

  // ===========================================================================
  // q
  // ===========================================================================

  private SqlPredicate compileText(String q, TypeSurface surface, List<String> notes) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String trimmed = q.trim();
    StringBuilder fragment = new StringBuilder("(");
    Map<String, Object> params = new LinkedHashMap<>();
    if (BooleanModeEscaper.shorterThanNgram(trimmed)) {
      notes.add(LIKE_NOTE);
      fragment.append("a.name LIKE :qLike OR a.display_name LIKE :qLike OR a.summary LIKE :qLike");
      params.put("qLike", likeValue(trimmed));
      params.put("qFt", "");
    } else {
      params.put("qFt", BooleanModeEscaper.toBooleanQuery(trimmed));
      params.put("qTerms", List.of(trimmed.split("\\s+")));
      fragment.append("MATCH(a.name, a.display_name, a.summary) AGAINST(:qFt IN BOOLEAN MODE)");
    }
    // 提槽 searchable 字段的 OR 追加（plan §4.2"再按 field_def.searchable=1 追加提槽列条件"）。
    // 按 (槽位,形状) 去重：tableColumn/standardField 共用的 s_str_1 只出现一次。
    Set<String> seen = new LinkedHashSet<>();
    for (SurfaceType type : surface.types()) {
      for (SlotField field : type.searchableSlots()) {
        String shape = switch (field.matchType() == null ? "text" : field.matchType().toLowerCase()) {
          case "exact" -> "EQ";
          case "like", "text" -> "LIKE";
          default -> null; // range 不参与单值 q
        };
        if (shape == null || !seen.add(field.slotColumn() + ":" + shape)) {
          continue;
        }
        if ("text".equalsIgnoreCase(field.matchType())) {
          notes.add("searchable 文本字段 " + field.fieldName() + " 的 q 命中按槽位 LIKE 执行"
              + "（FULLTEXT 面仅目录固有 name/display_name/summary 三列）");
        }
        if ("EQ".equals(shape)) {
          fragment.append(" OR ").append(field.slotColumn()).append(" = :qEq_").append(field.slotColumn());
          params.put("qEq_" + field.slotColumn(), trimmed);
        } else {
          fragment.append(" OR ").append(field.slotColumn()).append(" LIKE :qLike_").append(field.slotColumn());
          params.put("qLike_" + field.slotColumn(), likeValue(trimmed));
        }
      }
    }
    fragment.append(")");
    return new SqlPredicate(fragment.toString(), params);
  }

  /** LIKE 的通配符消毒：用户串里的 % 与 _ 不配当通配符（反斜杠先转，避免二次转义）。 */
  static String likeValue(String raw) {
    return "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  // ===========================================================================
  // 筛选
  // ===========================================================================

  private List<SqlPredicate> compileFilters(
      Map<String, Object> filters,
      TypeSurface surface,
      Map<String, Long> typeIdsByName,
      String prefix) {
    List<SqlPredicate> out = new ArrayList<>();
    if (filters == null || filters.isEmpty()) {
      return out;
    }
    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      String key = entry.getKey();
      Object raw = entry.getValue();
      if (raw == null) {
        continue;
      }
      if ("typeName".equals(key)) {
        out.add(compileTypeNameFilter(raw, typeIdsByName, prefix));
      } else if ("domainId".equals(key)) {
        out.add(compileDomainFilter(raw, prefix));
      } else if ("tagged".equals(key)) {
        boolean yes = asBoolean(key, raw);
        out.add(new SqlPredicate(
            (yes ? "EXISTS" : "NOT EXISTS")
                + " (SELECT 1 FROM yak_md_label l"
                + " WHERE l.project_id = a.project_id AND l.asset_id = a.id)",
            Map.of()));
      } else if ("hasSummary".equals(key)) {
        boolean yes = asBoolean(key, raw);
        out.add(new SqlPredicate(
            yes ? "(a.summary IS NOT NULL AND a.summary <> '')"
                : "(a.summary IS NULL OR a.summary = '')",
            Map.of()));
      } else if (key.startsWith("attr.")) {
        out.add(compileAttrFilter(key.substring("attr.".length()), raw, surface, prefix));
      } else {
        out.add(compileNativeFilter(key, raw, prefix));
      }
    }
    return out;
  }

  private SqlPredicate compileNativeFilter(String key, Object raw, String prefix) {
    String column = MetadataNativeFilterColumns.columnFor(key);
    if (column == null) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID,
          "不支持的筛选键: " + key + "（原生键见 MetadataNativeFilterColumns，扩展键须带 attr. 前缀）");
    }
    List<Object> values = asList(raw);
    if ("providerType".equals(key)) {
      values = values.stream().map(v -> {
        String token = String.valueOf(v).trim().toUpperCase();
        if (!PROVIDER_TYPES.contains(token)) {
          throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID, "providerType=" + v);
        }
        return (Object) token;
      }).toList();
    } else if ("entityStatus".equals(key)) {
      values = values.stream().map(v -> {
        MetadataEntityStatus status = MetadataEntityStatus.fromValue(String.valueOf(v));
        if (status == null) {
          throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID, "entityStatus=" + v);
        }
        return (Object) status.value();
      }).toList();
    } else if ("parentAssetId".equals(key)) {
      // BIGINT 列必须按数字绑定：字符串绑参会让 MySQL 隐式转换，索引当场失效。
      // datasourceId 相反——data_source_id 是 varchar，按数字绑会把隐式转换压到列上，
      // 一样废掉 idx_yak_metadata_asset_datasource，所以保持字符串形状。
      values = values.stream().map(v -> (Object) asLong(key, v)).toList();
    } else if ("datasourceId".equals(key)) {
      values = values.stream().map(v -> (Object) String.valueOf(v).trim()).toList();
    }
    return equalsOrIn(column, values, name(prefix, key));
  }

  private SqlPredicate compileTypeNameFilter(
      Object raw, Map<String, Long> typeIdsByName, String prefix) {
    List<Object> ids = asList(raw).stream().map(v -> {
      Long id = typeIdsByName.get(String.valueOf(v).trim());
      if (id == null) {
        throw new MetadataException(MetadataErrorCode.TYPE_NOT_FOUND, "typeName=" + v);
      }
      return (Object) id;
    }).toList();
    return equalsOrIn("a.type_id", ids, name(prefix, "typeName"));
  }

  private SqlPredicate compileDomainFilter(Object raw, String prefix) {
    List<Object> values = asList(raw);
    Map<String, Object> params = new LinkedHashMap<>();
    List<String> ors = new ArrayList<>();
    for (Object value : values) {
      String param = name(prefix, "domain");
      params.put(param, String.valueOf(value).trim());
      ors.add("POSITION(CONCAT(',', COALESCE(:" + param + ", ''), ',') IN CONCAT(',', a.domain_ids, ',')) > 0");
    }
    return new SqlPredicate("(" + String.join(" OR ", ors) + ")", params);
  }

  /** attr.&lt;field&gt;：声明与落点两级校验（49012 / 49024），形状只由 match_type 决定。 */
  private SqlPredicate compileAttrFilter(
      String fieldName, Object raw, TypeSurface surface, String prefix) {
    List<SlotField> declarations = new ArrayList<>();
    boolean declaredSomewhere = false;
    for (SurfaceType type : surface.types()) {
      if (type.declaredFields().contains(fieldName)) {
        declaredSomewhere = true;
      }
      type.searchableSlots().stream()
          .filter(f -> f.fieldName().equals(fieldName))
          .forEach(declarations::add);
    }
    if (!declaredSomewhere) {
      throw new MetadataException(MetadataErrorCode.ATTRIBUTE_NOT_DEFINED, "attr." + fieldName);
    }
    if (declarations.isEmpty()) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID,
          "attr." + fieldName + " 无查询落点（field_def 未提槽），只能展示不能筛");
    }
    SlotField field = declarations.get(0);
    String column = field.slotColumn();
    String matchType = field.matchType() == null ? "text" : field.matchType().toLowerCase();
    if ("range".equals(matchType)) {
      return compileRangeFilter(column, fieldName, raw, prefix);
    }
    List<Object> values = asList(raw);
    if ("exact".equals(matchType)) {
      List<Object> coerced = values.stream().map(v -> coerceSlot(column, fieldName, v)).toList();
      return equalsOrIn(column, coerced, name(prefix, fieldName));
    }
    // text / like：一律 LIKE，全表扫的账由 explain 标注、UI 展示（plan §4.5 的明码标价）。
    Map<String, Object> params = new LinkedHashMap<>();
    String param = name(prefix, fieldName);
    params.put(param, likeValue(String.valueOf(values.get(0))));
    return new SqlPredicate(column + " LIKE :" + param, params);
  }

  private SqlPredicate compileRangeFilter(String column, String fieldName, Object raw, String prefix) {
    Object fromValue;
    Object toValue;
    if (raw instanceof Collection<?> collection) {
      List<Object> pair = new ArrayList<>(collection);
      if (pair.isEmpty() || pair.size() > 2) {
        throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID,
            "range 值须为 [from,to]（一端可为 null）");
      }
      fromValue = pair.get(0);
      toValue = pair.size() > 1 ? pair.get(1) : null;
    } else {
      String[] parts = String.valueOf(raw).split("\\.\\.", -1);
      if (parts.length > 2) {
        throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID,
            "range 值格式: from..to（任一端可缺省）");
      }
      fromValue = parts[0].isEmpty() ? null : parts[0];
      toValue = parts.length == 2 && !parts[1].isEmpty() ? parts[1] : null;
    }
    List<String> conditions = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    if (fromValue != null) {
      String fromParam = name(prefix, fieldName + "_from");
      params.put(fromParam, coerceSlot(column, fieldName, fromValue));
      conditions.add(column + " >= :" + fromParam);
    }
    if (toValue != null) {
      String toParam = name(prefix, fieldName + "_to");
      params.put(toParam, coerceSlot(column, fieldName, toValue));
      conditions.add(column + " <= :" + toParam);
    }
    if (conditions.isEmpty()) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID, "range 两端不能同时为空");
    }
    return new SqlPredicate("(" + String.join(" AND ", conditions) + ")", params);
  }

  /** 槽位列类型由列名前段决定（s_str_* 文本 / s_num_* 与 s_bool_* 数值 / s_date_* 时间）。 */
  private static Object coerceSlot(String column, String fieldName, Object value) {
    if (column.startsWith("s_num_") || column.startsWith("s_bool_")) {
      if (value instanceof Boolean bool) {
        return bool ? 1 : 0;
      }
      return asLong(fieldName, value);
    }
    if (column.startsWith("s_date_")) {
      return asDateTime(fieldName, value);
    }
    return String.valueOf(value).trim();
  }

  // ===========================================================================
  // 游标 / 排序
  // ===========================================================================

  private SqlPredicate compileCursor(String searchAfter, String sortExpr, boolean ascending) {
    int sep = searchAfter.lastIndexOf(SearchPlan.CURSOR_SEPARATOR);
    if (sep <= 0 || sep == searchAfter.length() - 1) {
      throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT,
          "searchAfter 格式: <sortValue>|<id>");
    }
    String sortValue = searchAfter.substring(0, sep);
    long id;
    try {
      id = Long.parseLong(searchAfter.substring(sep + 1).trim());
    } catch (NumberFormatException e) {
      throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, "searchAfter 尾段须为数字 id");
    }
    Object cursorValue = switch (sortExpr) {
      case "a.id" -> id;
      case "a.update_time", "a.create_time" -> asDateTime("searchAfter", sortValue);
      default -> sortValue;
    };
    String op = ascending ? ">" : "<";
    return new SqlPredicate(
        "(" + sortExpr + " " + op + " :cursorV"
            + " OR (" + sortExpr + " = :cursorV AND a.id " + op + " :cursorId))",
        Map.of("cursorV", cursorValue, "cursorId", id));
  }

  private String resolveSortField(String sortField, boolean hasQ) {
    if (sortField == null || sortField.isBlank()) {
      // q 非空默认按类型权重粗排（§4.6 只做乘性加权，不承诺相关性质量），否则按更新时间。
      return hasQ ? "relevance" : "updateTime";
    }
    String trimmed = sortField.trim();
    if ("relevance".equals(trimmed) || SORT_COLUMNS.containsKey(trimmed)) {
      return trimmed;
    }
    throw new MetadataException(MetadataErrorCode.SEARCH_SORT_NOT_ALLOWED, "sortField=" + sortField);
  }

  /** type_def.search_default_weight 的乘性加权（§4.6），实现为 CASE 权重表，纯配置驱动。 */
  private static String buildWeightCase(TypeSurface surface, Map<String, Object> extraParams) {
    StringBuilder caseSql = new StringBuilder("CASE");
    int i = 0;
    for (SurfaceType type : surface.types()) {
      extraParams.put("wt" + i, type.id());
      extraParams.put("wv" + i, type.weight());
      caseSql.append(" WHEN a.type_id = :wt").append(i).append(" THEN :wv").append(i);
      i++;
    }
    return "(" + caseSql.append(" ELSE 1.0 END)").toString();
  }

  /** §4.6"命中 N 列"：默认面不含 tableColumn 且面上有 table 才算这笔账。 */
  private static Long resolveColumnRollup(TypeSurface surface, Map<String, Long> typeIdsByName) {
    boolean hasTable = surface.byName("table") != null;
    boolean hasColumn = surface.byName("tableColumn") != null;
    Long columnId = typeIdsByName.get("tableColumn");
    return hasTable && !hasColumn && columnId != null ? columnId : null;
  }

  // ===========================================================================
  // 小工具
  // ===========================================================================

  private static SqlPredicate equalsOrIn(String column, List<Object> values, String baseParam) {
    if (values.isEmpty()) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID, "空值列表: " + column);
    }
    if (values.size() == 1) {
      return new SqlPredicate(column + " = :" + baseParam, Map.of(baseParam, values.get(0)));
    }
    Map<String, Object> params = new LinkedHashMap<>();
    List<String> marks = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      String param = baseParam + "_" + i;
      params.put(param, values.get(i));
      marks.add(":" + param);
    }
    return new SqlPredicate(column + " IN (" + String.join(",", marks) + ")", params);
  }

  /** 具名参数只允许字母数字下划线；field_name 本身是 camelCase，这里兜住 attr 恶意键名。 */
  private String name(String prefix, String key) {
    return prefix + "_" + key.replaceAll("[^A-Za-z0-9_]", "_") + "_" + (paramSeq++);
  }

  private static int clampSize(int size, List<String> notes) {
    if (size <= 0) {
      return DEFAULT_SIZE;
    }
    if (size > MAX_SIZE) {
      notes.add("size 已收敛到上限 " + MAX_SIZE);
      return MAX_SIZE;
    }
    return size;
  }

  private static List<Object> asList(Object raw) {
    if (raw instanceof Collection<?> collection) {
      List<Object> out = new ArrayList<>();
      for (Object item : collection) {
        if (item != null) {
          out.add(item);
        }
      }
      if (out.isEmpty()) {
        throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID, "值集合为空");
      }
      return out;
    }
    return List.of(raw);
  }

  private static boolean asBoolean(String key, Object raw) {
    if (raw instanceof Boolean bool) {
      return bool;
    }
    String text = String.valueOf(raw).trim();
    if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
      return true;
    }
    if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
      return false;
    }
    throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID, key + " 须为布尔");
  }

  private static Long asLong(String key, Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID, key + " 须为数字: " + value);
    }
  }

  private static LocalDateTime asDateTime(String key, Object value) {
    if (value instanceof LocalDateTime dateTime) {
      return dateTime;
    }
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    String text = String.valueOf(value).trim();
    try {
      return LocalDateTime.parse(text);
    } catch (DateTimeParseException ignored) {
      // 继续尝试日期形态
    }
    try {
      return LocalDate.parse(text).atStartOfDay();
    } catch (DateTimeParseException e) {
      throw new MetadataException(MetadataErrorCode.SEARCH_FILTER_INVALID,
          key + " 须为 ISO 时间（yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-dd）: " + value);
    }
  }
}
