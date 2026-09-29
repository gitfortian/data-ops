package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.dao.mapper.AssetViewRecordMapper;
import io.yak.ops.common.bean.po.asset.AssetViewRecordPO;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 浏览流水单测(ticket 97):5 分钟去重、趋势映射。 */
class AssetViewRecordServiceTest {

  private AssetViewRecordMapper viewMapper;
  private AssetViewRecordService service;

  @BeforeEach
  void setUp() {
    viewMapper = Mockito.mock(AssetViewRecordMapper.class);
    CurrentProject currentProject = Mockito.mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(1L);
    service = new AssetViewRecordService(currentProject, viewMapper);
  }

  @Test
  void recordsOnceThenDedupesWithinWindow() {
    assertTrue(service.record(42L, "root", "detail"));
    assertFalse(service.record(42L, "root", "detail"));
    assertTrue(service.record(42L, "lucas", "search"));
    assertFalse(service.record(42L, "root", "detail"));

    ArgumentCaptor<AssetViewRecordPO> captor =
        ArgumentCaptor.forClass(AssetViewRecordPO.class);
    verify(viewMapper, times(2)).insert(captor.capture());
    AssetViewRecordPO first = captor.getAllValues().get(0);
    assertEquals(42L, first.getAssetId());
    assertEquals("root", first.getViewer());
    assertEquals("detail", first.getEntry());
    assertEquals(1L, first.getProjectId());
  }

  @Test
  void dedupeIsScopedPerProject() {
    CurrentProject multi = Mockito.mock(CurrentProject.class);
    when(multi.requireProjectId()).thenReturn(1L, 2L, 1L);
    AssetViewRecordService scoped = new AssetViewRecordService(multi, viewMapper);
    assertTrue(scoped.record(7L, "root", null));
    assertTrue(scoped.record(7L, "root", null));
    assertFalse(scoped.record(7L, "root", null));
    verify(viewMapper, times(2)).insert(any(AssetViewRecordPO.class));
  }

  @Test
  void trendAggregatesDailyCounts() {
    when(viewMapper.selectMaps(any())).thenReturn(List.of(
        Map.of("view_date", Date.valueOf("2026-09-18"), "view_count", 3L),
        Map.of("view_date", Date.valueOf("2026-09-19"), "view_count", 1L)));
    List<AssetViewRecordService.DailyView> trend = service.trend(42L, 30);
    assertEquals(2, trend.size());
    assertEquals("2026-09-18", trend.get(0).date());
    assertEquals(3L, trend.get(0).count());
    assertEquals(1L, trend.get(1).count());
  }
}
