package io.yak.ops.business.metadata.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 搜索编译器的契约测试（ticket 117 验收清单逐条对位）。
 *
 * <p>这里断言的全是"结构"而非执行结果——facet 里不该有的东西、游标该落在哪套谓词、
 * 未声明 attr 该以哪个错误码拒掉。执行侧的真机走查另在 §8。
 */
class SearchConditionBuilderTest {

  private static final Long PROJECT = 1L;

  // V1 基线的缩影：table 有 s_str_2(text)/s_num_1(range)，standardField 共用 s_str_1(exact)。
  private static final SurfaceType TABLE = new SurfaceType(
      3L, "table", "物理表", 2.0,
      List.of("databaseName", "tableName", "columnCount", "tableComment"),
      List.of(
          new SlotField("databaseName", "s_str_2", "text"),
          new SlotField("columnCount", "s_num_1", "range")));
  private static final SurfaceType STANDARD_FIELD = new SurfaceType(
      7L, "standardField", "标准字段", 1.0,
      List.of("fieldCode", "dataType"),
      List.of(new SlotField("dataType", "s_str_1", "exact")));
  private static final TypeSurface SURFACE = new TypeSurface(List.of(TABLE, STANDARD_FIELD));

  private static final Map<String, Long> ALL_TYPES = map(
      "table", 3L, "standardField", 7L, "tableColumn", 4L, "domain", 8L);

  private final SearchConditionBuilder builder = new SearchConditionBuilder();

  // ---- 验收：queryFilter 参与聚合计数、postFilter 不影响 ----------------------

  @Test
  void queryFilterJoinsFacetsButPostFilterNeverDoes() {
    SearchPlan plan = build(SURFACE, map("providerType", "HARVESTED"), map("owner", "alice"));

    String facetWhere = fragments(plan.facetPredicates());
    String rowWhere = fragments(plan.rowPredicates());
    assertThat(facetWhere).contains("a.provider_type = :qf_providerType_0");
    assertThat(facetWhere).doesNotContain("a.owner_user");
    assertThat(rowWhere).contains("a.owner_user = :pf_owner_1");
    // rollup 拿 queryFilter、绝不拿 postFilter（它回答的是"这些命中列属于哪张在算的表"）。
    assertThat(fragments(plan.rollupPredicates()))
        .contains("a.provider_type")
        .doesNotContain("a.owner_user");
  }

  // ---- 验收：attr 两级闸（未声明 49012 / 无落点 49024） ----------------------

  @Test
  void attrFilterOnUndeclaredFieldIsRejected() {
    assertThatThrownBy(() -> build(SURFACE, Map.of("attr.secret", "x"), Map.of()))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.ATTRIBUTE_NOT_DEFINED));
  }

  @Test
  void attrFilterOnDeclaredButUnslottedFieldIsRejected() {
    // tableComment 是登记过的字段但没提槽：只展示不能筛（plan §4.5 落点三态之③）。
    assertThatThrownBy(() -> build(SURFACE, Map.of("attr.tableComment", "x"), Map.of()))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.SEARCH_FILTER_INVALID));
  }

  @Test
  void attrShapesComeFromMatchTypeNotFromCodeBranches() {
    SearchPlan plan = build(SURFACE,
        map("attr.dataType", "varchar", "attr.columnCount", "10..20"), Map.of());
    String where = fragments(plan.facetPredicates());
    assertThat(where).contains("s_str_1 = :qf_dataType_0"); // exact → 等值
    assertThat(where).contains("s_num_1 >= :qf_columnCount_from_1"); // range → BETWEEN 两端
    assertThat(where).contains("s_num_1 <= :qf_columnCount_to_2");
  }

  // ---- 验收：BOOLEAN MODE 转义在服务层集中做 + 单字降级 ----------------------

  @Test
  void qGoesThroughPhraseQuotingAndSlotAugmentation() {
    SearchPlan plan = build(SURFACE, Map.of(), Map.of(), "订单>明细");
    assertThat(plan.booleanQuery()).isEqualTo("\"订单>明细\"");
    assertThat(plan.degradedToLike()).isFalse();
    String where = fragments(plan.rowPredicates());
    assertThat(where).contains("MATCH(a.name, a.display_name, a.summary) AGAINST(:qFt IN BOOLEAN MODE)");
    // searchable 提槽字段以 OR 追加；两类型共用的 s_str_1 只出现一次。
    assertThat(where).contains("s_str_2 LIKE :qLike_s_str_2");
    assertThat(where).contains("s_str_1 = :qEq_s_str_1");
    assertThat(where.indexOf("s_str_1 =")).isEqualTo(where.lastIndexOf("s_str_1 ="));
  }

  @Test
  void singleCharacterQueryDegradesToLikeAndSaysSo() {
    SearchPlan plan = build(SURFACE, Map.of(), Map.of(), "表");
    assertThat(plan.degradedToLike()).isTrue();
    assertThat(fragments(plan.rowPredicates())).contains("a.name LIKE :qLike");
    assertThat(plan.explainNotes()).anyMatch(note -> note.contains("降级 LIKE"));
  }

  @Test
  void likeWildcardsInUserInputAreSanitized() {
    // databaseName 是 text 匹配的提槽字段：attr 过滤走 LIKE 分支。
    SearchPlan plan = build(SURFACE, Map.of("attr.databaseName", "a%b_c"), Map.of());
    Object bound = plan.facetPredicates().stream()
        .flatMap(p -> p.params().entrySet().stream())
        .filter(e -> e.getValue() instanceof String s && s.contains("a\\%b\\_c"))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
    assertThat(bound).isEqualTo("%a\\%b\\_c%");
  }

  // ---- 验收：searchAfter 游标替代 offset 深翻 --------------------------------

  @Test
  void cursorOnlyEntersRowPredicatesAndRequiresSingleColumnSort() {
    SearchPlan first = build(
        SURFACE, Map.of(), Map.of(), null, "updateTime", "desc", null, 0, 20);
    assertThat(first.sortValueExpr()).isEqualTo("a.update_time");
    assertThat(first.rowPredicates()).noneMatch(p -> p.fragment().contains("cursorV"));

    SearchPlan paged = build(
        SURFACE, Map.of(), Map.of(), null, "updateTime", "desc", "2026-09-01T00:00:00|42", 0, 20);
    String rowWhere = fragments(paged.rowPredicates());
    assertThat(rowWhere).contains("(a.update_time < :cursorV OR (a.update_time = :cursorV AND a.id < :cursorId))");
    assertThat(fragments(paged.facetPredicates())).doesNotContain("cursorV");

    // relevance 排序没有可寻址的 sortValue——直接拒，而不是悄悄给一个错游标。
    assertThatThrownBy(() -> build(
        SURFACE, Map.of(), Map.of(), "订单", "relevance", "desc", "2.0|42", 0, 20))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.INVALID_ARGUMENT));
  }

  // ---- 验收：trackTotalHits 默认 false + 无界禁止 ----------------------------

  @Test
  void offsetDeepPagingIsRefusedButCursorPageIgnoresFrom() {
    assertThatThrownBy(() -> build(SURFACE, Map.of(), Map.of(), null, null, null, null, 6000, 20))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.INVALID_ARGUMENT));

    SearchPlan withCursor = build(
        SURFACE, Map.of(), Map.of(), null, "name", "asc", "abc|7", 300, 20);
    assertThat(withCursor.offset()).isZero();
    assertThat(withCursor.explainNotes()).anyMatch(note -> note.contains("from 被忽略"));
  }

  @Test
  void sizeIsClampedNotRejected() {
    SearchPlan plan = build(SURFACE, Map.of(), Map.of(), null, null, null, null, 0, 5000);
    assertThat(plan.size()).isEqualTo(SearchConditionBuilder.MAX_SIZE);
    assertThat(plan.explainNotes()).anyMatch(note -> note.contains("收敛到上限"));
  }

  // ---- 验收：§4.6 列淹没的账一期就还 ----------------------------------------

  @Test
  void columnRollupIsArmedWhenTableColumnStaysOffSurface() {
    SearchPlan plan = build(SURFACE, Map.of(), Map.of(), "订单");
    assertThat(plan.columnRollupTypeId()).isEqualTo(4L);
    assertThat(plan.extraParams()).containsEntry("rollupTypeId", 4L);
    // rollup 谓词不含 index 收窄（a.type_id IN），否则默认面外的列永远算不出命中。
    assertThat(fragments(plan.rollupPredicates())).doesNotContain("a.type_id IN");
  }

  @Test
  void explicitTableColumnIndexDisarmsRollupAndRowsComeDirectly() {
    SurfaceType column = new SurfaceType(4L, "tableColumn", "物理列", 0.2,
        List.of("columnName", "dataType"), List.of());
    SearchPlan plan = build(new TypeSurface(List.of(TABLE, column)), Map.of(), Map.of(), "user_id");
    assertThat(plan.columnRollupTypeId()).isNull();
    assertThat(fragments(plan.rowPredicates())).contains("a.type_id IN (:surfaceIds)");
  }

  @Test
  void nativeIdFiltersBindInTheirOwnColumnShape() {
    // 「命中 N 列」点下去就是这条：index 换成 tableColumn + parentAssetId 收窄到该表。
    SearchPlan drill = build(SURFACE, map("parentAssetId", "17"), Map.of());
    assertThat(fragments(drill.rowPredicates())).contains("a.parent_asset_id = :qf_parentAssetId");
    // 字符串绑在 BIGINT 列上会让 MySQL 隐式转换、索引失效，故收敛成 Long。
    assertThat(lastParams(drill).values()).containsExactly(17L);

    // 反过来：data_source_id 是 varchar，按数字绑会把隐式转换压到列上，一样废索引。
    SearchPlan bySource = build(SURFACE, map("datasourceId", "3"), Map.of());
    assertThat(fragments(bySource.rowPredicates())).contains("a.data_source_id = :qf_datasourceId");
    assertThat(lastParams(bySource).values()).containsExactly("3");

    assertThatThrownBy(() -> build(SURFACE, Map.of("parentAssetId", "abc"), Map.of()))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.SEARCH_FILTER_INVALID));
  }

  private static Map<String, Object> lastParams(SearchPlan plan) {
    List<SqlPredicate> facets = plan.facetPredicates();
    return facets.get(facets.size() - 1).params();
  }

  @Test
  void defaultSortWeightsByTypeWhenQueryPresent() {
    SearchPlan plan = build(SURFACE, Map.of(), Map.of(), "订单");
    assertThat(plan.orderBySql()).startsWith("(CASE WHEN a.type_id = :wt0 THEN :wv0");
    assertThat((Double) plan.extraParams().get("wv0")).isEqualTo(2.0);
    assertThat((Double) plan.extraParams().get("wv1")).isEqualTo(1.0);
    // 无 q 的浏览模式按更新时间，不发权重 CASE。
    SearchPlan browse = build(SURFACE, Map.of(), Map.of(), null);
    assertThat(browse.orderBySql()).isEqualTo("a.update_time DESC, a.id DESC");
  }

  // ---- 其它闸：白名单外的东西进不了 SQL --------------------------------------

  @Test
  void sortFieldOutsideWhitelistIsRejected() {
    assertThatThrownBy(() -> build(SURFACE, Map.of(), Map.of(), null, "password", null, null, 0, 20))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.SEARCH_SORT_NOT_ALLOWED));
  }

  @Test
  void unknownFilterKeysAndValuesAreRejected() {
    assertThatThrownBy(() -> build(SURFACE, Map.of("assetType", "TABLE"), Map.of()))
        .isInstanceOfSatisfying(MetadataException.class, e -> {
          assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.SEARCH_FILTER_INVALID);
          assertThat(e.getMessage()).contains("assetType");
        });
    // asset_type 被点名拒绝正是命名规则的要求：它会把 table 与 dataModel 显示成同一类型。
    assertThatThrownBy(() -> build(SURFACE, Map.of("providerType", "ODD"), Map.of()))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.SEARCH_FILTER_INVALID));
  }

  @Test
  void typeNameFilterResolvesThroughMetamodelNotAssetType() {
    SearchPlan plan = build(SURFACE, Map.of("typeName", "tableColumn"), Map.of());
    String where = fragments(plan.facetPredicates());
    assertThat(where).contains("a.type_id = :qf_typeName_0");
    assertThat(plan.facetPredicates()).anyMatch(p -> p.params().containsValue(4L));
    assertThatThrownBy(() -> build(SURFACE, Map.of("typeName", "ghost"), Map.of()))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.TYPE_NOT_FOUND));
  }

  @Test
  void entityStatusIsNormalizedToStoredLiteral() {
    SearchPlan plan = build(SURFACE, Map.of("entityStatus", "approved"), Map.of());
    plan.facetPredicates().forEach(p -> assertThat(p.params().values()).doesNotContain("approved"));
    assertThat(plan.facetPredicates().stream()
        .flatMap(p -> p.params().values().stream())
        .anyMatch(v -> "Approved".equals(v))).isTrue();
  }

  @Test
  void multiValueFiltersBecomeInListsAndDomainBecomesFindInSet() {
    SearchPlan plan = build(SURFACE, map(
        "databaseName", List.of("dwd", "ads"), "domainId", List.of(7L, 8L)), Map.of());
    String where = fragments(plan.facetPredicates());
    assertThat(where).contains("a.database_name IN (:qf_databaseName_0_0,:qf_databaseName_0_1)");
    assertThat(where).contains("POSITION(CONCAT(',', COALESCE(:qf_domain_1, ''), ',') IN CONCAT(',', a.domain_ids, ','))");
    assertThat(where).contains("POSITION(CONCAT(',', COALESCE(:qf_domain_2, ''), ',') IN CONCAT(',', a.domain_ids, ','))");
  }

  @Test
  void emptySurfaceIsRefusedBeforeAnySqlShape() {
    assertThatThrownBy(() -> build(new TypeSurface(List.of()), Map.of(), Map.of(), null))
        .isInstanceOfSatisfying(MetadataException.class, e ->
            assertThat(e.getErrorCode()).isEqualTo(MetadataErrorCode.SEARCH_FILTER_INVALID));
  }

  // ===========================================================================
  // 装配小工具
  // ===========================================================================

  private SearchPlan build(
      TypeSurface surface, Map<String, Object> queryFilter, Map<String, Object> postFilter) {
    return build(surface, queryFilter, postFilter, null);
  }

  private SearchPlan build(
      TypeSurface surface, Map<String, Object> queryFilter, Map<String, Object> postFilter, String q) {
    return build(surface, queryFilter, postFilter, q, null, null, null, 0, 20);
  }

  private SearchPlan build(
      TypeSurface surface,
      Map<String, Object> queryFilter,
      Map<String, Object> postFilter,
      String q,
      String sortField,
      String sortOrder,
      String searchAfter,
      int from,
      int size) {
    SearchQuery query = new SearchQuery(
        q, List.of(), queryFilter, postFilter, sortField, sortOrder, searchAfter, from, size, false);
    return builder.build(PROJECT, query, surface, ALL_TYPES);
  }

  private static String fragments(List<SqlPredicate> predicates) {
    return String.join(" AND ", predicates.stream().map(SqlPredicate::fragment).toList());
  }

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      out.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return out;
  }

  private static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    Map<K, V> out = new LinkedHashMap<>();
    out.put(k1, v1);
    out.put(k2, v2);
    out.put(k3, v3);
    out.put(k4, v4);
    return out;
  }
}
