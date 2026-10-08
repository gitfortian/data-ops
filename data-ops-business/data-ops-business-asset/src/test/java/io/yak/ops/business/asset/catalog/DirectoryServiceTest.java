package io.yak.ops.business.asset.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.dao.mapper.AssetDirectoryMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.asset.dao.model.AssetDirectoryPO;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 目录树单测(ticket 92):编码/物化路径、防环、删除门槛、模板初始化幂等。 */
class DirectoryServiceTest {

  private AssetDirectoryMapper directoryMapper;
  private AssetItemMapper itemMapper;
  private LayerConfigApi layerConfigApi;
  private ProcessApi processApi;
  private DirectoryService service;

  @BeforeEach
  void setUp() {
    directoryMapper = Mockito.mock(AssetDirectoryMapper.class);
    itemMapper = Mockito.mock(AssetItemMapper.class);
    layerConfigApi = Mockito.mock(LayerConfigApi.class);
    processApi = Mockito.mock(ProcessApi.class);
    CurrentProject currentProject = Mockito.mock(CurrentProject.class);
    BusinessAuditService auditService = Mockito.mock(BusinessAuditService.class);
    AuditOperationHandle handle = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    AtomicLong idSeq = new AtomicLong(100);
    lenient().when(directoryMapper.insert(any(AssetDirectoryPO.class))).thenAnswer(inv -> {
      inv.<AssetDirectoryPO>getArgument(0).setId(idSeq.incrementAndGet());
      return 1;
    });
    service = new DirectoryService(currentProject, directoryMapper, itemMapper,
        layerConfigApi, processApi, auditService);
  }

  @Test
  void treeUsesTheSameProjectionForRootAndNestedDirectories() {
    AssetDirectoryPO root = dir(5L, "root", "/5/", 1);
    root.setParentId(0L);
    root.setIconKey("database");
    root.setDescription("数据目录");
    AssetDirectoryPO child = dir(9L, "child", "/5/9/", 0);
    child.setParentId(5L);
    child.setSortOrder(null);
    when(directoryMapper.selectList(any())).thenReturn(List.of(root, child));

    List<DirectoryService.DirNode> nodes = service.tree();

    assertEquals(1, nodes.size());
    DirectoryService.DirNode parent = nodes.get(0);
    assertEquals(5L, parent.id());
    assertEquals("database", parent.iconKey());
    assertEquals("数据目录", parent.description());
    assertTrue(parent.builtin());
    assertEquals(1, parent.children().size());
    DirectoryService.DirNode nested = parent.children().get(0);
    assertEquals("child", nested.dirCode());
    assertEquals("/5/9/", nested.path());
    assertEquals(0, nested.sortOrder());
    assertTrue(nested.children().isEmpty());
    verify(directoryMapper, times(1)).selectList(any());
  }

  @Test
  void templateInitializationMaterializesBuiltinDirectoryPath() {
    when(directoryMapper.selectCount(any())).thenReturn(0L);
    when(layerConfigApi.listLayers()).thenReturn(List.of(
        new io.yak.ops.business.semantic.api.WarehouseLayer(
            10L, "DWD", "明细层", null, null, null, null, null, null, null,
            10, "ENABLED", false, true, "system", null, null)));
    when(processApi.listDomains()).thenReturn(List.of());
    assertEquals(1, service.initTemplate("root"));

    org.mockito.ArgumentCaptor<AssetDirectoryPO> saved =
        org.mockito.ArgumentCaptor.forClass(AssetDirectoryPO.class);
    verify(directoryMapper).updateById(saved.capture());
    assertEquals("/101/", saved.getValue().getPath());
    assertEquals("dwd", saved.getValue().getDirCode());
    assertTrue(saved.getValue().getBuiltin());
  }

  @Test
  void createUnderRootMaterializesPathAndAutoCode() {
    when(directoryMapper.selectCount(any())).thenReturn(0L);
    when(directoryMapper.selectList(any())).thenReturn(List.of());
    DirectoryService.DirNode node =
        service.create(null, null, "交易域", "database", null, null, "root");
    assertEquals("dir_1", node.dirCode());
    assertEquals("/101/", node.path());
    assertEquals(10, node.sortOrder());
    assertTrue(!node.builtin());
    verify(directoryMapper).updateById(any(AssetDirectoryPO.class));
  }

  @Test
  void createUnderParentUsesParentCodePrefix() {
    AssetDirectoryPO parent = dir(5L, "dwd", "/1/", 0);
    when(directoryMapper.selectOne(any())).thenReturn(parent);
    when(directoryMapper.selectCount(any())).thenReturn(2L, 0L);
    when(directoryMapper.selectList(any())).thenReturn(List.of());
    DirectoryService.DirNode node =
        service.create(5L, null, "子目录", null, null, null, "root");
    assertEquals("dwd_3", node.dirCode());
    assertEquals("/1/101/", node.path());
  }

  @Test
  void moveRejectsCycleIntoOwnSubtree() {
    when(directoryMapper.selectOne(any()))
        .thenReturn(dir(7L, "a", "/7/", 0))
        .thenReturn(dir(9L, "b", "/7/9/", 7));
    AssetException ex =
        assertThrows(AssetException.class, () -> service.move(7L, 9L, "root"));
    assertEquals(AssetErrorCode.DIRECTORY_INVALID, ex.getErrorCode());
  }

  @Test
  void moveRejectsSelf() {
    when(directoryMapper.selectOne(any())).thenReturn(dir(7L, "a", "/7/", 0));
    AssetException ex = assertThrows(AssetException.class, () -> service.move(7L, 7L, "root"));
    assertEquals(AssetErrorCode.DIRECTORY_INVALID, ex.getErrorCode());
  }

  @Test
  void deleteBlocksBuiltinAndNonEmpty() {
    when(directoryMapper.selectOne(any())).thenReturn(dir(3L, "x", "/3/", 1));
    AssetException builtinEx = assertThrows(AssetException.class, () -> service.delete(3L, "r"));
    assertEquals(AssetErrorCode.ILLEGAL_STATE_OPERATION, builtinEx.getErrorCode());

    when(directoryMapper.selectOne(any())).thenReturn(dir(4L, "y", "/4/", 0));
    when(directoryMapper.selectCount(any())).thenReturn(2L);
    AssetException emptyEx = assertThrows(AssetException.class, () -> service.delete(4L, "r"));
    assertEquals(AssetErrorCode.DIRECTORY_NOT_EMPTY, emptyEx.getErrorCode());
  }

  @Test
  void deleteSucceedsWhenLeafAndEmpty() {
    when(directoryMapper.selectOne(any())).thenReturn(dir(4L, "y", "/4/", 0));
    when(directoryMapper.selectCount(any())).thenReturn(0L);
    service.delete(4L, "r");
    verify(directoryMapper).updateById(any(AssetDirectoryPO.class));
  }

  @Test
  void initTemplateRejectsWhenAlreadyInitialized() {
    when(directoryMapper.selectCount(any())).thenReturn(3L);
    AssetException ex =
        assertThrows(AssetException.class, () -> service.initTemplate("r"));
    assertEquals(AssetErrorCode.TEMPLATE_ALREADY_INITIALIZED, ex.getErrorCode());
    verify(directoryMapper, never()).insert(any(AssetDirectoryPO.class));
  }

  @Test
  void moveAssetsSkipsMissingAndAuditsNothingWhenZero() {
    when(itemMapper.selectOne(any())).thenReturn(null);
    assertEquals(0, service.moveAssets(List.of(404L), null, "r"));
    verify(itemMapper, never()).updateById(any(AssetItemPO.class));
  }

  private static AssetDirectoryPO dir(Long id, String code, String path, int builtin) {
    AssetDirectoryPO po = new AssetDirectoryPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setDirCode(code);
    po.setDirName(code);
    po.setParentId(path.equals("/") ? 0L : 1L);
    po.setPath(path);
    po.setSortOrder(0);
    po.setBuiltin(builtin == 1);
    po.setDeleted(false);
    return po;
  }
}
