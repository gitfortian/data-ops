package io.yak.ops.business.modeling.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.lineage.ModelingLineageRegistrationService;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * MODEL provider 契约单测:asset_key 与血缘登记键逐字符一致(D6),游标分页与刷新语义。
 */
class ModelAssetProviderTest {

  private ModelingModelMapper mapper;
  private ModelAssetProvider provider;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(ModelingModelMapper.class);
    provider = new ModelAssetProvider(mapper);
  }

  @Test
  void assetKeyMatchesLineageRegistrationKeyExactly() {
    when(mapper.selectList(any())).thenReturn(List.of(model(42L, "dwd_order", false)));
    AssetPage page = provider.cursorList(new AssetCursorQuery(1L, null, null, 10));
    AssetDescriptor item = page.items().get(0);
    assertEquals("modeling:model:42", item.assetKey());
    assertEquals(ModelingLineageRegistrationService.modelAssetKey(42L), item.assetKey());
    assertEquals("42", item.sourceId());
  }

  @Test
  void descriptorMapsTableModel() {
    when(mapper.selectList(any())).thenReturn(List.of(model(7L, "ods_x", false)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("ODS 订单表", item.name());
    assertEquals(AssetType.TABLE, item.assetType());
    assertEquals(AssetSourceType.MODEL, provider.sourceType());
    assertEquals("ODS", item.layerCode());
    assertNull(item.domainCode());
    assertEquals("alice", item.suggestedOwner());
    assertEquals("ods_x", item.extra().get("modelCode"));
    assertEquals("PUBLISHED", item.extra().get("status"));
    assertTrue(item.contentHash().length() == 64);
  }

  @Test
  void cursorPageAdvancesOnlyWhenFull() {
    when(mapper.selectList(any())).thenReturn(
        List.of(model(1L, "a", false), model(2L, "b", false)));
    assertEquals("2",
        provider.cursorList(new AssetCursorQuery(1L, null, null, 2)).nextCursor());
    assertNull(
        provider.cursorList(new AssetCursorQuery(1L, null, null, 5)).nextCursor());
    when(mapper.selectList(any())).thenReturn(List.of());
    AssetPage empty = provider.cursorList(new AssetCursorQuery(1L, null, "2", 5));
    assertNull(empty.nextCursor());
    assertTrue(empty.items().isEmpty());
  }

  @Test
  void refreshSkipsMissingDeletedAndBadIds() {
    when(mapper.selectById(3L)).thenReturn(model(3L, "m", true));
    assertEquals(Optional.empty(), provider.refresh("3"));
    when(mapper.selectById(4L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("4"));
    assertEquals(Optional.empty(), provider.refresh("abc"));
    assertEquals(Optional.empty(), provider.refresh(null));
    when(mapper.selectById(5L)).thenReturn(model(5L, "m", false));
    assertEquals("modeling:model:5", provider.refresh("5").orElseThrow().assetKey());
  }

  private static ModelingModelPO model(Long id, String code, boolean deleted) {
    ModelingModelPO po = new ModelingModelPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setModelCode(code);
    po.setModelName("ODS 订单表");
    po.setDialect("DORIS");
    po.setLayerCode("ODS");
    po.setStatus("PUBLISHED");
    po.setCreatedBy("alice");
    po.setUpdateTime(LocalDateTime.now());
    po.setDeleted(deleted);
    return po;
  }
}
