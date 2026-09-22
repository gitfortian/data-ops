package io.yak.ops.business.metadata.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link MetadataSearchBackend} 的一期唯一实现（plan §4.4）。
 *
 * <p>SQL 全部经 {@link NamedParameterJdbcTemplate} 具名绑定：谓词文本只来自
 * {@link SearchConditionBuilder} 从白名单渲染出的字面量，用户输入没有一条路径进 SQL 串。
 *
 * <p>一次检索最多四条语句、条数<b>不随类型数增长</b>（本票"每类一次查询直接判不通过"的账就在这）：
 * ① 行集；② 类型分桶（同源 WHERE 的 GROUP BY type_id）；③ 可选的"命中 N 列"回账；
 * ④ 可选的层级父实体批量回查（一次 IN，不是 N+1）。
 *
 * <p>读哪些列、一行翻成什么形状都在 {@link CatalogRowRead}，与详情聚合共用同一条读路径。
 */
@Component
public class MysqlMetadataSearchBackend implements MetadataSearchBackend {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public MysqlMetadataSearchBackend(
      @Qualifier("yakBusinessDataSource") DataSource dataSource, ObjectMapper objectMapper) {
    this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "mysql";
  }

  @Override
  public SearchResult search(SearchPlan plan) {
    // ① 行集：row 谓词（含 queryFilter + q + postFilter + 游标）。
    MapSqlParameterSource rowParams = params(plan.extraParams(), plan.rowPredicates());
    rowParams.addValue("opsLimit", plan.size());
    rowParams.addValue("opsOffset", plan.offset());
    String cursorSelect =
        plan.sortValueExpr() == null ? "" : ", " + plan.sortValueExpr() + " AS sortCursor";
    String rowSql =
        "SELECT " + CatalogRowRead.SELECT_COLUMNS + cursorSelect
            + " FROM yak_metadata_asset a WHERE " + where(plan.rowPredicates())
            + " ORDER BY " + plan.orderBySql()
            + " LIMIT :opsLimit OFFSET :opsOffset";
    List<String> sqls = new ArrayList<>();
    sqls.add(rowSql);
    List<Map<String, Object>> rows = new ArrayList<>();
    List<Object> cursors = new ArrayList<>();
    boolean hasCursor = plan.sortValueExpr() != null;
    jdbc.query(rowSql, rowParams, (ResultSet rs, int rowNum) -> {
      Map<String, Object> row = CatalogRowRead.mapRow(rs, objectMapper);
      if (hasCursor) {
        cursors.add(CatalogRowRead.normalize(rs.getObject("sortCursor")));
      }
      rows.add(row);
      return row;
    });

    // ② 类型分桶：与行集同 WHERE、只去掉 postFilter 与游标——facet/行一致性由此结构保证。
    MapSqlParameterSource facetParams = params(plan.extraParams(), plan.facetPredicates());
    String facetSql =
        "SELECT a.type_id AS typeId, COUNT(*) AS hitCount FROM yak_metadata_asset a WHERE "
            + where(plan.facetPredicates()) + " GROUP BY a.type_id";
    sqls.add(facetSql);
    Map<Long, Long> facets = new LinkedHashMap<>();
    // 逐行回调必须是 void 形状：写成带 return 的一参 lambda 会静默绑到 ResultSetExtractor，
    // 那样整段只对空游标跑一次，取值直接抛 "before start"。
    jdbc.query(
        facetSql,
        facetParams,
        (RowCallbackHandler) rs -> facets.put(rs.getLong("typeId"), rs.getLong("hitCount")));

    // ③ 列命中回账（plan §4.6）：跨 index 面的谓词 + 固定 type_id=tableColumn。
    Map<Long, Long> columnHits = null;
    if (plan.columnRollupTypeId() != null) {
      MapSqlParameterSource rollupParams = params(plan.extraParams(), plan.rollupPredicates());
      String rollupSql =
          "SELECT a.parent_asset_id AS parentId, COUNT(*) AS hitCount FROM yak_metadata_asset a"
              + " WHERE " + where(plan.rollupPredicates())
              + " AND a.type_id = :rollupTypeId AND a.parent_asset_id IS NOT NULL"
              + " GROUP BY a.parent_asset_id";
      sqls.add(rollupSql);
      columnHits = new HashMap<>();
      Map<Long, Long> hits = columnHits;
      jdbc.query(
          rollupSql,
          rollupParams,
          (RowCallbackHandler) rs -> hits.put(rs.getLong("parentId"), rs.getLong("hitCount")));
    }

    // ④ 层级：一批父 id 一次回查（getHierarchy=1 才发这条）。
    if (plan.getHierarchy()) {
      attachParents(plan.projectId(), rows, sqls);
    }

    String nextCursor = null;
    if (hasCursor && !rows.isEmpty() && !cursors.isEmpty()) {
      Object cursorValue = cursors.get(cursors.size() - 1);
      if (cursorValue != null) {
        nextCursor = cursorValue + SearchPlan.CURSOR_SEPARATOR + rows.get(rows.size() - 1).get("id");
      }
    }
    return new SearchResult(List.copyOf(rows), Map.copyOf(facets), columnHits, nextCursor,
        List.copyOf(sqls));
  }

  private void attachParents(Long projectId, List<Map<String, Object>> rows, List<String> sqls) {
    Set<Long> parentIds = new LinkedHashSet<>();
    for (Map<String, Object> row : rows) {
      if (row.get("parentAssetId") instanceof Long parentId) {
        parentIds.add(parentId);
      }
    }
    if (parentIds.isEmpty()) {
      return;
    }
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("projectId", projectId)
        .addValue("parentIds", parentIds);
    Map<Long, Map<String, Object>> parents = new HashMap<>();
    String parentSql =
        "SELECT a.id, a.asset_key AS assetKey, a.name, a.display_name AS displayName,"
            + " a.type_id AS typeId FROM yak_metadata_asset a"
            + " WHERE a.project_id = :projectId AND a.id IN (:parentIds)";
    sqls.add(parentSql);
    jdbc.query(
        parentSql,
        params,
        (RowCallbackHandler)
            rs -> {
              Map<String, Object> parent = new LinkedHashMap<>();
              long id = rs.getLong("id");
              parent.put("id", id);
              parent.put("assetKey", rs.getString("assetKey"));
              parent.put("name", rs.getString("name"));
              parent.put("displayName", rs.getString("displayName"));
              parent.put("typeId", CatalogRowRead.normalize(rs.getObject("typeId")));
              parents.put(id, parent);
            });
    for (Map<String, Object> row : rows) {
      if (row.get("parentAssetId") instanceof Long parentId && parents.containsKey(parentId)) {
        row.put("parent", parents.get(parentId));
      }
    }
  }

  private static MapSqlParameterSource params(Map<String, Object> extra, List<SqlPredicate> predicates) {
    MapSqlParameterSource source = new MapSqlParameterSource(extra);
    for (SqlPredicate predicate : predicates) {
      source.addValues(predicate.params());
    }
    return source;
  }

  private static String where(List<SqlPredicate> predicates) {
    return String.join(" AND ", predicates.stream().map(SqlPredicate::fragment).toList());
  }
}
