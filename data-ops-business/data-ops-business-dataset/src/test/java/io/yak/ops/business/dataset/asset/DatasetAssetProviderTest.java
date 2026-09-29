package io.yak.ops.business.dataset.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.dataset.dao.mapper.DatasetMapper;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.lineage.DatasetLineageSynchronizer;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** DATASET provider 契约:asset_key 与血缘登记键逐字符一致(D6),游标/刷新语义。 */
class DatasetAssetProviderTest {

  private static final java.time.LocalDateTime UPDATED = java.time.LocalDateTime.now();

  private DatasetMapper mapper;
  private DatasetAssetProvider provider;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(DatasetMapper.class);
    provider = new DatasetAssetProvider(mapper);
  }

  @Test
  void assetKeyMatchesLineageRegistrationKeyExactly() {
    when(mapper.selectList(any())).thenReturn(List.of(dataset(42L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("dataset:42", item.assetKey());
    assertEquals(DatasetLineageSynchronizer.datasetAssetKey(42L), item.assetKey());
    assertEquals("42", item.sourceId());
    assertEquals(AssetSourceType.DATASET, provider.sourceType());
  }

  @Test
  void descriptorMapsDatasetFields() {
    when(mapper.selectList(any())).thenReturn(List.of(dataset(7L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("订单明细数据集", item.name());
    assertEquals("描述", item.description());
    assertEquals(AssetType.DATASET, item.assetType());
    assertNull(item.layerCode());
    assertNull(item.suggestedOwner());
    assertEquals("PUBLISHED", item.extra().get("status"));
    assertEquals("3", item.extra().get("currentVersionId"));
    assertEquals(64, item.contentHash().length());
    assertEquals(UPDATED.withNano(0), item.updatedAt().withNano(0));
  }

  @Test
  void cursorPageAdvancesOnlyWhenFull() {
    when(mapper.selectList(any())).thenReturn(List.of(dataset(1L), dataset(2L)));
    assertEquals("2", provider.cursorList(new AssetCursorQuery(1L, null, null, 2)).nextCursor());
    assertNull(provider.cursorList(new AssetCursorQuery(1L, null, null, 5)).nextCursor());
    when(mapper.selectList(any())).thenReturn(List.of());
    AssetPage empty = provider.cursorList(new AssetCursorQuery(1L, null, "2", 5));
    assertTrue(empty.items().isEmpty());
    assertNull(empty.nextCursor());
  }

  @Test
  void refreshSkipsMissingAndBadIds() {
    when(mapper.selectById(4L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("4"));
    assertEquals(Optional.empty(), provider.refresh("abc"));
    assertEquals(Optional.empty(), provider.refresh(null));
    when(mapper.selectById(5L)).thenReturn(dataset(5L));
    assertEquals("dataset:5", provider.refresh("5").orElseThrow().assetKey());
  }

  private static DatasetPO dataset(Long id) {
    DatasetPO po = new DatasetPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setName("订单明细数据集");
    po.setDescription("描述");
    po.setStatus("PUBLISHED");
    po.setCurrentVersionId(3L);
    po.setUpdateTime(Timestamp.valueOf(UPDATED));
    return po;
  }
}
