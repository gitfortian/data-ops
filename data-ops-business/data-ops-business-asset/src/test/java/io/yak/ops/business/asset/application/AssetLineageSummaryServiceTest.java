package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetLineageSummaryService.LineageSummary;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** M2-3 跨域引用摘要:只引用血缘/质量既有事实,缺源即降级,不伪造计数。 */
class AssetLineageSummaryServiceTest {

  @SuppressWarnings("unchecked")
  private final ObjectProvider<LineageQueryService> lineageProvider = mock(ObjectProvider.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<QualityMonitorReader> qualityProvider = mock(ObjectProvider.class);

  private AssetLineageSummaryService service;

  @BeforeEach
  void setUp() {
    service = new AssetLineageSummaryService(lineageProvider, qualityProvider);
  }

  @Test
  void lineageNotWiredIsUnavailableWithNoCounts() {
    when(lineageProvider.getIfAvailable()).thenReturn(null);

    LineageSummary summary = service.summarize("metric:1", 3);

    assertFalse(summary.available());
    assertEquals("血缘服务未装配", summary.reason());
    assertEquals(-1, summary.upstreamTables());
    assertEquals(-1, summary.unauditedTables());
  }

  @Test
  void unknownAssetKeyIsUnavailable() {
    LineageQueryService lineage = mock(LineageQueryService.class);
    when(lineage.getAssetByKey("metric:404")).thenReturn(null);
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    LineageSummary summary = service.summarize("metric:404", 3);

    assertFalse(summary.available());
    assertEquals("血缘域暂无该资产登记", summary.reason());
    assertEquals(-1, summary.unauditedTables());
  }

  @Test
  void lineageFailureDegradesWithReason() {
    LineageQueryService lineage = mock(LineageQueryService.class);
    when(lineage.getAssetByKey("metric:1")).thenThrow(new IllegalArgumentException("缺少项目上下文"));
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    LineageSummary summary = service.summarize("metric:1", 3);

    assertFalse(summary.available());
    assertTrue(summary.reason().startsWith("血缘查询失败"));
    assertEquals(-1, summary.upstreamTables());
  }

  @Test
  void qualityNotWiredReportsUpstreamButNeverAudited() {
    stubLineage(table(2L, "7", "db", "", "t_order"), table(3L, "7", "db", "", "t_user"));
    when(qualityProvider.getIfAvailable()).thenReturn(null);

    LineageSummary summary = service.summarize("metric:1", 3);

    assertFalse(summary.available());
    assertEquals("质量域未装配", summary.reason());
    assertEquals(2, summary.upstreamTables());
    assertEquals(-1, summary.unauditedTables());
    assertEquals(3, summary.depth());
  }

  @Test
  void allUpstreamAuditedPassesWithZeroGap() {
    stubLineage(table(2L, "7", "db", "", "T_ORDER"));
    stubQuality(List.of(new TableMonitorSummary(
        "t_order", 3L, "订单监控", 1, 4, CheckResult.PASSED, LocalDateTime.now())));

    LineageSummary summary = service.summarize("metric:1", 3);

    assertTrue(summary.available());
    assertNull(summary.reason());
    assertEquals(1, summary.upstreamTables());
    assertEquals(0, summary.unauditedTables());
  }

  @Test
  void missingMonitorAndNonPassedResultBothCountAsUnaudited() {
    stubLineage(table(2L, "7", "db", "", "t_order"), table(3L, "7", "db", "", "t_user"));
    stubQuality(List.of(new TableMonitorSummary(
        "t_order", 3L, "订单监控", 1, 4, CheckResult.NOT_PASSED, LocalDateTime.now())));

    LineageSummary summary = service.summarize("metric:1", 3);

    // t_order 有监控但最近执行未通过、t_user 没有监控行——都算未稽核
    assertTrue(summary.available());
    assertEquals(2, summary.upstreamTables());
    assertEquals(2, summary.unauditedTables());
  }

  @Test
  void sameTableViaMultipleEdgesCountsOnceAndRootAndNonTableExcluded() {
    LineageAsset root = table(1L, "7", "db", "", "t_target");
    stubLineage(root,
        table(2L, "7", "db", "", "T_Order"),
        table(9L, "7", "db", "", "t_order"),
        column(4L, "7", "db", "", "t_user", "id"));
    stubQuality(List.of(new TableMonitorSummary(
        "t_order", 3L, "订单监控", 1, 4, CheckResult.PASSED, LocalDateTime.now())));

    LineageSummary summary = service.summarize("metric:1", 2);

    // 根(t_target 与根同 id 剔除)、列节点剔除、t_order 两条边按四元组去重只计一次
    assertEquals(1, summary.upstreamTables());
    assertEquals(0, summary.unauditedTables());
    assertEquals(2, summary.depth());
  }

  @Test
  void unlocatableFourTupleCountsAsUnauditedWithoutQueryingQuality() {
    stubLineage(table(2L, "not-a-datasource", "db", "", "t_order"));
    QualityMonitorReader reader = mock(QualityMonitorReader.class);
    when(qualityProvider.getIfAvailable()).thenReturn(reader);

    LineageSummary summary = service.summarize("metric:1", 3);

    assertTrue(summary.available());
    assertEquals(1, summary.unauditedTables());
    verify(reader, never()).tableSummaries(anyLong(), anyString(), any());
  }

  @Test
  void qualityScopeFailureCountsTableAsUnauditedAndQueriesScopeOnce() {
    stubLineage(table(2L, "7", "db", "s1", "t_order"), table(3L, "7", "db", "s1", "t_user"));
    QualityMonitorReader reader = mock(QualityMonitorReader.class);
    when(reader.tableSummaries(anyLong(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("质量库不可达"));
    when(qualityProvider.getIfAvailable()).thenReturn(reader);

    LineageSummary summary = service.summarize("metric:1", 3);

    assertTrue(summary.available());
    assertEquals(2, summary.unauditedTables());
    // 同一 (ds,db,schema) 作用域只回源一次,而不是每表查一遍
    verify(reader, times(1)).tableSummaries(eq(7L), eq("db"), eq("s1"));
  }

  private void stubLineage(LineageAsset... nodes) {
    LineageQueryService lineage = mock(LineageQueryService.class);
    LineageAsset root = mock(LineageAsset.class);
    when(root.id()).thenReturn(1L);
    when(root.assetType()).thenReturn(LineageAssetType.METRIC);
    when(lineage.getAssetByKey("metric:1")).thenReturn(root);
    when(lineage.graph(eq(1L), eq(LineageDirection.UPSTREAM), anyInt()))
        .thenReturn(new LineageGraph(
            root, LineageDirection.UPSTREAM, 0, List.of(nodes), List.of()));
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);
  }

  private void stubQuality(List<TableMonitorSummary> summaries) {
    QualityMonitorReader reader = mock(QualityMonitorReader.class);
    when(reader.tableSummaries(anyLong(), anyString(), any())).thenReturn(summaries);
    when(qualityProvider.getIfAvailable()).thenReturn(reader);
  }

  private static LineageAsset table(long id, String ds, String db, String schema, String tableName) {
    LineageAsset asset = mock(LineageAsset.class);
    when(asset.id()).thenReturn(id);
    when(asset.assetType()).thenReturn(LineageAssetType.TABLE);
    when(asset.dataSourceId()).thenReturn(ds);
    when(asset.databaseName()).thenReturn(db);
    when(asset.schemaName()).thenReturn(schema);
    when(asset.tableName()).thenReturn(tableName);
    return asset;
  }

  private static LineageAsset column(long id, String ds, String db, String schema,
      String tableName, String columnName) {
    LineageAsset asset = table(id, ds, db, schema, tableName);
    when(asset.assetType()).thenReturn(LineageAssetType.COLUMN);
    when(asset.columnName()).thenReturn(columnName);
    return asset;
  }
}
