package io.yak.ops.business.development.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.development.dao.mapper.DevelopmentNodeMapper;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import io.yak.ops.business.development.dao.model.DevelopmentNodePO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** TASK provider 契约:asset_key=sql-task:data-development:{id} 与血缘登记键同源(D6)。 */
class TaskAssetProviderTest {

  private DevelopmentNodeMapper mapper;
  private TaskAssetProvider provider;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(DevelopmentNodeMapper.class);
    provider = new TaskAssetProvider(mapper);
  }

  @Test
  void assetKeyMatchesLineageRegistrationKeyExactly() {
    when(mapper.selectList(any())).thenReturn(List.of(node(55L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("sql-task:data-development:55", item.assetKey());
    assertEquals(DevelopmentSqlLineageService.sqlTaskAssetKey(55L), item.assetKey());
    assertEquals("55", item.sourceId());
    assertEquals(AssetSourceType.TASK, provider.sourceType());
  }

  @Test
  void descriptorMapsSqlTaskNode() {
    when(mapper.selectList(any())).thenReturn(List.of(node(7L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("订单宽表加工", item.name());
    assertEquals(AssetType.TASK, item.assetType());
    assertEquals("lucas", item.suggestedOwner());
    assertEquals("SQL", item.extra().get("type"));
    assertEquals("true", item.extra().get("configured"));
    assertEquals(64, item.contentHash().length());
    assertTrue(item.updatedAt() != null);
  }

  @Test
  void cursorAndRefreshSemantics() {
    when(mapper.selectList(any())).thenReturn(List.of(node(1L), node(2L)));
    assertEquals("2", provider.cursorList(new AssetCursorQuery(1L, null, null, 2)).nextCursor());
    assertNull(provider.cursorList(new AssetCursorQuery(1L, null, null, 5)).nextCursor());
    when(mapper.selectById(4L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("4"));
    assertEquals(Optional.empty(), provider.refresh("nan"));
    when(mapper.selectById(5L)).thenReturn(node(5L));
    assertEquals("sql-task:data-development:5", provider.refresh("5").orElseThrow().assetKey());
  }

  private static DevelopmentNodePO node(Long id) {
    DevelopmentNodePO po = new DevelopmentNodePO();
    po.setId(id);
    po.setProjectId(1L);
    po.setName("订单宽表加工");
    po.setType("SQL");
    po.setConfigured(true);
    po.setUpdatedBy("lucas");
    po.setUpdateTime(Instant.now());
    po.setDeleted(false);
    return po;
  }
}
