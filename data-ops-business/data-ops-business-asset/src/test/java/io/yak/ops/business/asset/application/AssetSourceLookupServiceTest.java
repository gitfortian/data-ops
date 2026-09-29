package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class AssetSourceLookupServiceTest {

  @Test
  void returnsExactRegistryProjectionForStableDatasetIdentity() {
    CurrentProject currentProject = mock(CurrentProject.class);
    AssetItemMapper mapper = mock(AssetItemMapper.class);
    when(currentProject.requireProjectId()).thenReturn(23L);
    AssetItemPO row = new AssetItemPO();
    row.setId(101L);
    row.setProjectId(23L);
    row.setSourceType("DATASET");
    row.setSourceId("55");
    row.setAssetKey("dataset:55");
    row.setAssetType("DATASET");
    row.setStatus("PUBLISHED");
    row.setDeleted(false);
    when(mapper.selectOne(any())).thenReturn(row);

    var result = new AssetSourceLookupService(currentProject, mapper).lookup("dataset", "55");

    assertEquals("FOUND", result.state());
    assertEquals(101L, result.assetId());
    assertEquals("dataset:55", result.assetKey());
    assertEquals("55", result.sourceId());
    verify(currentProject).requireProjectId();
  }

  @Test
  void reportsNotIndexedInsteadOfPretendingTheDatasetDoesNotExist() {
    CurrentProject currentProject = mock(CurrentProject.class);
    AssetItemMapper mapper = mock(AssetItemMapper.class);
    when(currentProject.requireProjectId()).thenReturn(23L);
    when(mapper.selectOne(any())).thenReturn(null);

    var result = new AssetSourceLookupService(currentProject, mapper).lookup("DATASET", "55");

    assertEquals("NOT_INDEXED", result.state());
    assertNull(result.assetId());
    assertEquals("DATASET", result.sourceType());
  }
}
