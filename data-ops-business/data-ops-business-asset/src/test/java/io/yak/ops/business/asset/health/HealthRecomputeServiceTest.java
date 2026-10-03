package io.yak.ops.business.asset.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.asset.dao.mapper.AssetChangeRecordMapper;
import io.yak.ops.business.asset.dao.mapper.AssetHealthSnapshotMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.dao.mapper.AssetViewRecordMapper;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.asset.dao.model.AssetHealthSnapshotPO;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** 健康度重算单测(ticket 98):全量流程、单项失败遏制、快照分层聚合。 */
class HealthRecomputeServiceTest {

  private AssetItemMapper itemMapper;
  private AssetChangeRecordMapper changeMapper;
  private AssetTagRelMapper tagRelMapper;
  private AssetViewRecordMapper viewMapper;
  private AssetHealthSnapshotMapper snapshotMapper;
  private HealthRecomputeService service;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
    TableInfoHelper.initTableInfo(assistant, AssetItemPO.class);
    TableInfoHelper.initTableInfo(assistant, io.yak.ops.business.asset.dao.model.AssetTagRelPO.class);
    TableInfoHelper.initTableInfo(assistant,
        io.yak.ops.business.asset.dao.model.AssetChangeRecordPO.class);
    TableInfoHelper.initTableInfo(assistant,
        io.yak.ops.business.asset.dao.model.AssetViewRecordPO.class);
    TableInfoHelper.initTableInfo(assistant, AssetHealthSnapshotPO.class);
    itemMapper = mock(AssetItemMapper.class);
    changeMapper = mock(AssetChangeRecordMapper.class);
    tagRelMapper = mock(AssetTagRelMapper.class);
    viewMapper = mock(AssetViewRecordMapper.class);
    snapshotMapper = mock(AssetHealthSnapshotMapper.class);
    service = new HealthRecomputeService(itemMapper, changeMapper, tagRelMapper, viewMapper,
        snapshotMapper, mock(ObjectProvider.class), mock(ObjectProvider.class));
  }

  @Test
  void recomputeAllAggregatesWritesScoresAndPurges() {
    AssetItemPO po = item(1L, "MODEL");
    po.setViewCount30d(null);
    when(viewMapper.selectMaps(any()))
        .thenReturn(List.of(row("asset_id", 1L, "cnt", 7L)));
    when(itemMapper.selectList(any())).thenReturn(List.of(po));
    when(tagRelMapper.selectCount(any())).thenReturn(2L);
    when(changeMapper.selectCount(any())).thenReturn(0L);
    when(viewMapper.delete(any())).thenReturn(3);

    var report = service.recomputeAll(1L);

    assertEquals(1, report.assets());
    assertEquals(1, report.viewRowsWritten());
    assertEquals(3, report.purgedViews());
    // 第一次 updateById 是浏览缓存 patch,第二次携带评分
    ArgumentCaptor<AssetItemPO> captor = ArgumentCaptor.forClass(AssetItemPO.class);
    verify(itemMapper, times(2)).updateById(captor.capture());
    AssetItemPO patch = captor.getAllValues().get(0);
    assertNull(patch.getHealthScore());
    assertEquals(7, patch.getViewCount30d());
    AssetItemPO scored = captor.getAllValues().get(1);
    assertEquals(7, scored.getViewCount30d());
    assertNotNull(scored.getHealthScore());
    assertNotNull(scored.getHealthGrade());
    assertTrue(scored.getHealthDetail().startsWith("["));
    verify(viewMapper).delete(any());
  }

  @Test
  void unavailableExtensionsDropScoreButNeverFabricate() {
    // TABLE + 血缘/安全服务未装配:UNAVAILABLE 项进分母计 0 分
    AssetItemPO po = item(2L, "TABLE");
    po.setViewCount30d(0);
    when(viewMapper.selectMaps(any())).thenReturn(List.of());
    when(itemMapper.selectList(any())).thenReturn(List.of(po));
    when(itemMapper.selectOne(any())).thenReturn(po);
    when(tagRelMapper.selectCount(any())).thenReturn(0L);
    when(changeMapper.selectCount(any())).thenReturn(0L);

    service.recomputeItems(1L, List.of(2L));

    ArgumentCaptor<AssetItemPO> captor = ArgumentCaptor.forClass(AssetItemPO.class);
    verify(itemMapper).updateById(captor.capture());
    AssetItemPO scored = captor.getValue();
    assertTrue(scored.getHealthScore() < 60);
    assertTrue(scored.getHealthDetail().contains("UNAVAILABLE"));
  }

  @Test
  void recomputeItemsContainsSingleAssetFailure() {
    AssetItemPO healthy = item(2L, "MODEL");
    healthy.setViewCount30d(0);
    when(viewMapper.selectMaps(any())).thenReturn(List.of());
    when(itemMapper.selectOne(any()))
        .thenThrow(new RuntimeException("db blip"))
        .thenReturn(healthy);
    when(tagRelMapper.selectCount(any())).thenReturn(0L);
    when(changeMapper.selectCount(any())).thenReturn(0L);

    service.recomputeItems(1L, List.of(1L, 2L));

    verify(itemMapper, times(1)).updateById(any(AssetItemPO.class));
  }

  @Test
  void recomputeItemsSkipsMissingAsset() {
    when(viewMapper.selectMaps(any())).thenReturn(List.of());
    when(itemMapper.selectOne(any())).thenReturn(null);

    service.recomputeItems(1L, List.of(404L));

    verify(itemMapper, never()).updateById(any(AssetItemPO.class));
  }

  @Test
  void snapshotDailyAggregatesLayersPlusAll() {
    when(itemMapper.selectMaps(any())).thenReturn(List.of(
        row("layer", "DWD", "grade", "A", "total", 2L, "published", 1L),
        row("layer", "DWS", "grade", "D", "total", 1L, "published", 0L)));
    when(snapshotMapper.selectOne(any())).thenReturn(null);

    int layers = service.snapshotDaily(1L);

    assertEquals(3, layers);
    ArgumentCaptor<AssetHealthSnapshotPO> captor =
        ArgumentCaptor.forClass(AssetHealthSnapshotPO.class);
    verify(snapshotMapper, times(3)).insert(captor.capture());
    AssetHealthSnapshotPO all = captor.getAllValues().stream()
        .filter(p -> "ALL".equals(p.getLayerCode())).findFirst().orElseThrow();
    assertEquals(2, all.getGradeACount());
    assertEquals(1, all.getGradeDCount());
    assertEquals(1, all.getPublishedCount());
  }

  @Test
  void snapshotDailyUpsertsExistingRow() {
    AssetHealthSnapshotPO existing = new AssetHealthSnapshotPO();
    existing.setId(99L);
    when(itemMapper.selectMaps(any())).thenReturn(List.of(
        row("layer", "DWD", "grade", "B", "total", 4L, "published", 4L)));
    when(snapshotMapper.selectOne(any())).thenReturn(existing);

    service.snapshotDaily(1L);

    verify(snapshotMapper, never()).insert(any(AssetHealthSnapshotPO.class));
    ArgumentCaptor<AssetHealthSnapshotPO> captor =
        ArgumentCaptor.forClass(AssetHealthSnapshotPO.class);
    // DWD + ALL 两行均命中已有记录 → 各自 updateById
    verify(snapshotMapper, times(2)).updateById(captor.capture());
    assertEquals(99L, captor.getAllValues().get(0).getId());
  }

  private static AssetItemPO item(Long id, String assetType) {
    AssetItemPO po = new AssetItemPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setAssetKey("modeling:model:" + id);
    po.setSourceType("MODEL");
    po.setAssetType(assetType);
    po.setName("资产" + id);
    po.setStatus("PUBLISHED");
    po.setDeleted(false);
    po.setUpdateTime(LocalDateTime.now());
    return po;
  }

  private static Map<String, Object> row(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return map;
  }
}
