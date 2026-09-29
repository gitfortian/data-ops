package io.yak.ops.business.metadata.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.metadata.dao.mapper.CatalogTableAssetProviderMapper;
import io.yak.ops.business.metadata.dao.model.CatalogTableAssetRow;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.common.util.metadata.PhysicalTableAssetKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** METADATA provider 契约单测：assetKey 直通血缘归一键（D6），游标分页与刷新语义。 */
class MetadataTableAssetProviderTest {

  private CatalogTableAssetProviderMapper mapper;
  private MetadataTableAssetProvider provider;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(CatalogTableAssetProviderMapper.class);
    provider = new MetadataTableAssetProvider(mapper);
  }

  @Test
  void assetKeyPassesThroughLineageNormalizedKey() {
    CatalogTableAssetRow row = table(42L, "ods", "t_order");
    when(mapper.selectProviderPage(eq(1L), isNull(), isNull(), eq(10))).thenReturn(List.of(row));
    AssetPage page = provider.cursorList(new AssetCursorQuery(1L, null, null, 10));
    AssetDescriptor item = page.items().get(0);
    assertEquals(row.getAssetKey(), item.assetKey());
    assertEquals(PhysicalTableAssetKey.of("7", "ods", "", "t_order"), item.assetKey());
    assertEquals("42", item.sourceId());
    assertEquals(AssetSourceType.METADATA, provider.sourceType());
  }

  @Test
  void descriptorPrefersDisplayNameAndDegradesHonestly() {
    CatalogTableAssetRow named = table(7L, "dw", "dwd_order");
    named.setDisplayName("订单明细");
    named.setOwnerUser("alice");
    when(mapper.selectProviderPage(any(), any(), any(), eq(10))).thenReturn(List.of(named));
    AssetDescriptor item = provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("订单明细", item.name());
    assertEquals(AssetType.TABLE, item.assetType());
    assertEquals("DWD", item.layerCode());
    assertNull(item.domainCode());
    assertEquals("alice", item.suggestedOwner());
    assertEquals("dw", item.extra().get("databaseName"));
    assertEquals("dwd_order", item.extra().get("tableName"));
    assertEquals(64, item.contentHash().length());

    // 采集行无 display_name/owner：回退物理名、归属留空，不伪造
    CatalogTableAssetRow bare = table(8L, "ods", "t_raw");
    bare.setDisplayName(null);
    bare.setOwnerUser(null);
    when(mapper.selectProviderPage(any(), any(), any(), eq(10))).thenReturn(List.of(bare));
    AssetDescriptor bareItem =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("t_raw", bareItem.name());
    assertNull(bareItem.suggestedOwner());
  }

  @Test
  void cursorPageAdvancesOnlyWhenFull() {
    when(mapper.selectProviderPage(any(), any(), any(), eq(2)))
        .thenReturn(List.of(table(1L, "ods", "a"), table(2L, "ods", "b")));
    assertEquals("2", provider.cursorList(new AssetCursorQuery(1L, null, null, 2)).nextCursor());
    when(mapper.selectProviderPage(any(), any(), any(), eq(5)))
        .thenReturn(List.of(table(1L, "ods", "a"), table(2L, "ods", "b")));
    assertNull(provider.cursorList(new AssetCursorQuery(1L, null, null, 5)).nextCursor());
    when(mapper.selectProviderPage(any(), any(), any(), eq(5))).thenReturn(List.of());
    AssetPage empty = provider.cursorList(new AssetCursorQuery(1L, null, "2", 5));
    assertTrue(empty.items().isEmpty());
    assertNull(empty.nextCursor());
  }

  @Test
  void refreshSkipsMissingAndBadIds() {
    when(mapper.selectProviderRowById(4L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("4"));
    assertEquals(Optional.empty(), provider.refresh("abc"));
    assertEquals(Optional.empty(), provider.refresh(null));
    when(mapper.selectProviderRowById(5L)).thenReturn(table(5L, "ods", "t"));
    assertEquals(table(5L, "ods", "t").getAssetKey(), provider.refresh("5").orElseThrow().assetKey());
  }

  private static CatalogTableAssetRow table(Long id, String db, String tbl) {
    CatalogTableAssetRow row = new CatalogTableAssetRow();
    row.setId(id);
    row.setAssetKey(PhysicalTableAssetKey.of("7", db, "", tbl));
    row.setName(tbl);
    row.setSummary("comment for " + tbl);
    row.setLayerCode("DWD");
    row.setEntityStatus("ACTIVE");
    row.setDataSourceId("7");
    row.setDatabaseName(db);
    row.setTableName(tbl);
    row.setUpdateTime(LocalDateTime.now());
    return row;
  }
}
