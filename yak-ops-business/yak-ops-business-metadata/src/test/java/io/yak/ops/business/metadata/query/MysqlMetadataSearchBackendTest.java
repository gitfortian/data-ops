package io.yak.ops.business.metadata.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 执行侧的接缝测试（ticket 117）。
 *
 * <p>这里守的是一条编译器不会报错的坑：一参 lambda 若带 {@code return}，会静默绑到
 * {@code ResultSetExtractor} 而不是逐行回调 —— 于是聚合语句只对着"游标停在首行之前"的
 * 空结果集跑一次，取值当场抛 SQLException，整个搜索端点变成 999。真机踩过一次，所以
 * 用 mock 的 JDBC 把"逐行回调真的被调到了"钉住，而不是等浏览器来发现。
 */
class MysqlMetadataSearchBackendTest {

  private static final SurfaceType TABLE = new SurfaceType(
      3L, "table", "物理表", 2.0, List.of("tableName"), List.of());
  private static final Map<String, Long> ALL_TYPES =
      Map.of("table", 3L, "tableColumn", 4L);

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void aggregateAndParentQueriesRunAsRowCallbacksNotOneShotExtractors() throws Exception {
    // 行集：一条 table 行，带 parentAssetId 以便逼出层级回查。
    ResultSet assetRow = mock(ResultSet.class);
    when(assetRow.getLong("id")).thenReturn(21L);
    when(assetRow.getObject("parentAssetId")).thenReturn(9L);
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenAnswer(
            (Answer<List<Map<String, Object>>>)
                invocation -> List.of(
                    (Map<String, Object>)
                        ((RowMapper) invocation.getArgument(2)).mapRow(assetRow, 0)));

    // 三条聚合/回查语句（facet、列命中 rollup、层级父实体）必须走逐行回调。
    ResultSet facetRow = mock(ResultSet.class);
    when(facetRow.getLong("typeId")).thenReturn(3L);
    when(facetRow.getLong("hitCount")).thenReturn(7L);
    ResultSet rollupRow = mock(ResultSet.class);
    when(rollupRow.getLong("parentId")).thenReturn(21L);
    when(rollupRow.getLong("hitCount")).thenReturn(4L);
    ResultSet parentRow = mock(ResultSet.class);
    when(parentRow.getLong("id")).thenReturn(9L);
    when(parentRow.getString("name")).thenReturn("ods_order");
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  RowCallbackHandler callback = invocation.getArgument(2);
                  String sql = invocation.getArgument(0);
                  if (sql.contains("a.type_id AS typeId")) callback.processRow(facetRow);
                  if (sql.contains("a.parent_asset_id AS parentId")) callback.processRow(rollupRow);
                  if (sql.contains("a.id IN (:parentIds)")) callback.processRow(parentRow);
                  return null;
                })
        .when(jdbc)
        .query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));

    SearchResult result = backend().search(plan());

    assertThat(result.typeFacets()).containsExactly(Map.entry(3L, 7L));
    assertThat(result.columnHits()).containsExactly(Map.entry(21L, 4L));
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).get("parent"))
        .as("层级回查按行装配，不是一次性抽取")
        .isInstanceOf(Map.class);
    assertThat(result.renderedSql()).hasSize(4);
  }

  private MysqlMetadataSearchBackend backend() throws Exception {
    MysqlMetadataSearchBackend backend =
        new MysqlMetadataSearchBackend(mock(DataSource.class), new ObjectMapper());
    Field field = MysqlMetadataSearchBackend.class.getDeclaredField("jdbc");
    field.setAccessible(true);
    field.set(backend, jdbc);
    return backend;
  }

  private static SearchPlan plan() {
    SearchQuery query =
        new SearchQuery(
            "订单",
            List.of("table"),
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            0,
            20,
            true);
    return new SearchConditionBuilder()
        .build(1L, query, new TypeSurface(List.of(TABLE)), ALL_TYPES);
  }
}
