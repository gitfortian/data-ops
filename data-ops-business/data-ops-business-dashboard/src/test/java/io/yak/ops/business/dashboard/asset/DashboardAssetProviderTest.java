package io.yak.ops.business.dashboard.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.dashboard.dao.mapper.DashboardMapper;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import io.yak.ops.business.dashboard.lineage.DashboardLineageSynchronizer;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** DASHBOARD provider 契约:asset_key=dashboard:{id} 与血缘登记键同源(D6)。 */
class DashboardAssetProviderTest {

  private DashboardMapper mapper;
  private DashboardAssetProvider provider;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(DashboardMapper.class);
    provider = new DashboardAssetProvider(mapper);
  }

  @Test
  void assetKeyMatchesLineageRegistrationKeyExactly() {
    when(mapper.selectList(any())).thenReturn(List.of(dashboard(42L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("dashboard:42", item.assetKey());
    assertEquals(DashboardLineageSynchronizer.dashboardAssetKey(42L), item.assetKey());
    assertEquals("42", item.sourceId());
    assertEquals(AssetSourceType.DASHBOARD, provider.sourceType());
  }

  @Test
  void descriptorMapsDashboardFields() {
    when(mapper.selectList(any())).thenReturn(List.of(dashboard(7L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("经营看板", item.name());
    assertEquals(AssetType.DASHBOARD, item.assetType());
    assertNull(item.suggestedOwner());
    assertEquals("2", item.extra().get("publishedVersionNo"));
    assertEquals("3", item.extra().get("currentVersionNo"));
    assertEquals(64, item.contentHash().length());
  }

  @Test
  void cursorAndRefreshSemantics() {
    when(mapper.selectList(any())).thenReturn(List.of(dashboard(1L), dashboard(2L)));
    assertEquals("2", provider.cursorList(new AssetCursorQuery(1L, null, null, 2)).nextCursor());
    assertNull(provider.cursorList(new AssetCursorQuery(1L, null, null, 5)).nextCursor());
    when(mapper.selectById(4L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("4"));
    assertEquals(Optional.empty(), provider.refresh("bad"));
    when(mapper.selectById(5L)).thenReturn(dashboard(5L));
    assertEquals("dashboard:5", provider.refresh("5").orElseThrow().assetKey());
  }

  private static DashboardPO dashboard(Long id) {
    DashboardPO po = new DashboardPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setName("经营看板");
    po.setDescription("描述");
    po.setCurrentVersionNo(3);
    po.setPublishedVersionNo(2);
    po.setUpdateTime(Timestamp.valueOf(LocalDateTime.now()));
    return po;
  }
}
