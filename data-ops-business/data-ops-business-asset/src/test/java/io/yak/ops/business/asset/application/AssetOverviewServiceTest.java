package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.asset.dao.mapper.AssetChangeRecordMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 驾驶舱概览单测(ticket 98):固定 8 查询预算、KPI/待办换算、空表不伪造。 */
class AssetOverviewServiceTest {

  private AssetItemMapper itemMapper;
  private AssetChangeRecordMapper changeMapper;
  private AssetOverviewService service;

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetItemPO.class);
    itemMapper = mock(AssetItemMapper.class);
    changeMapper = mock(AssetChangeRecordMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(1L);
    service = new AssetOverviewService(currentProject, itemMapper, changeMapper);
  }

  @Test
  @SuppressWarnings("unchecked")
  void overviewStaysWithinEightQueriesAndComputesKpis() {
    when(itemMapper.selectMaps(any())).thenReturn(
        List.of(row("k", "PUBLISHED", "c", 6L), row("k", "PENDING", "c", 3L),
            row("k", "SOURCE_GONE", "c", 1L)),
        List.of(row("k", "A", "c", 2L), row("k", "B", "c", 3L), row("k", "D", "c", 1L)),
        List.of(row("k", "TABLE", "c", 5L), row("k", "DOC", "c", 5L)),
        List.of(row("k", "DWD", "c", 4L), row("k", "UNSET", "c", 6L)),
        List.of(row("total", 10L, "no_owner", 2L, "added_30d", 4L, "unclassified", 3L)));
    when(changeMapper.selectCount(any())).thenReturn(5L);
    when(itemMapper.selectList(any())).thenReturn(
        List.of(listed(1L, "订单明细表")), List.of(listed(2L, "周度报表")));

    Map<String, Object> view = service.overview();

    Map<String, Object> kpis = (Map<String, Object>) view.get("kpis");
    assertEquals(10L, n(kpis, "total").longValue());
    assertEquals(6L, n(kpis, "published").longValue());
    assertEquals(3L, n(kpis, "pending").longValue());
    assertEquals(4L, n(kpis, "added30d").longValue());
    assertEquals(0.8, n(kpis, "ownerCoverage").doubleValue(), 1e-9);
    assertEquals(0.7, n(kpis, "classifiedRate").doubleValue(), 1e-9);
    assertEquals(2L, n(kpis, "gradeACount").longValue());
    assertEquals(1L, n(kpis, "gradeDCount").longValue());

    Map<String, Object> todos = (Map<String, Object>) view.get("todos");
    assertEquals(3L, n(todos, "pendingPublish").longValue());
    assertEquals(5L, n(todos, "openChanges").longValue());
    assertEquals(2L, n(todos, "noOwner").longValue());
    assertEquals(1L, n(todos, "gradeD").longValue());
    assertEquals(1L, n(todos, "sourceGone").longValue());

    List<Map<String, Object>> recent =
        (List<Map<String, Object>>) view.get("recentListed");
    assertEquals(1, recent.size());
    assertEquals(1L, ((Number) recent.get(0).get("assetId")).longValue());
    assertNotNull(recent.get(0).get("at"));

    // 查询预算:4 组 group by + 1 聚合 + 1 变更计数 + 2 最近动态 = 8
    verify(itemMapper, times(5)).selectMaps(any());
    verify(itemMapper, times(2)).selectList(any());
    verify(changeMapper, times(1)).selectCount(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void emptyLedgerYieldsZeroedKpisWithoutFabrication() {
    when(itemMapper.selectMaps(any())).thenReturn(List.of());
    when(changeMapper.selectCount(any())).thenReturn(0L);
    when(itemMapper.selectList(any())).thenReturn(List.of());

    Map<String, Object> view = service.overview();

    Map<String, Object> kpis = (Map<String, Object>) view.get("kpis");
    assertEquals(0L, n(kpis, "total").longValue());
    assertEquals(0.0, n(kpis, "ownerCoverage").doubleValue(), 1e-9);
    assertEquals(0.0, n(kpis, "classifiedRate").doubleValue(), 1e-9);
    assertTrue(((Map<String, Object>) view.get("distributions")).containsKey("grade"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void nullAggRowIsTolerated() {
    // COUNT(*) 在无行时返回 0 行不可能,但防御空结果集不抛 NPE
    when(itemMapper.selectMaps(any())).thenReturn(List.of(), List.of(), List.of(), List.of(),
        List.of());
    when(changeMapper.selectCount(any())).thenReturn(0L);
    when(itemMapper.selectList(any())).thenReturn(List.of());

    Map<String, Object> view = service.overview();
    assertEquals(0L, n((Map<String, Object>) view.get("kpis"), "total").longValue());
  }

  private static AssetItemPO listed(Long id, String name) {
    AssetItemPO po = new AssetItemPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setName(name);
    po.setAssetType("TABLE");
    po.setStatus("PUBLISHED");
    po.setOwner("root");
    po.setLastListedAt(LocalDateTime.now());
    po.setLastOfflineAt(LocalDateTime.now());
    return po;
  }

  private static Map<String, Object> row(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return map;
  }

  private static Number n(Map<String, Object> map, String key) {
    return (Number) map.get(key);
  }
}
