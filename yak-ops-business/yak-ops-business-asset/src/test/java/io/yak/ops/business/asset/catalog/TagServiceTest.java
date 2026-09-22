package io.yak.ops.business.asset.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.po.asset.AssetTagPO;
import io.yak.ops.common.bean.po.asset.AssetTagRelPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 标签服务单测(ticket 93):编码唯一、幂等打标、删除联动。 */
class TagServiceTest {

  private AssetTagMapper tagMapper;
  private AssetTagRelMapper tagRelMapper;
  private AssetItemMapper itemMapper;
  private TagService service;

  @BeforeEach
  void setUp() {
    tagMapper = Mockito.mock(AssetTagMapper.class);
    tagRelMapper = Mockito.mock(AssetTagRelMapper.class);
    itemMapper = Mockito.mock(AssetItemMapper.class);
    CurrentProject currentProject = Mockito.mock(CurrentProject.class);
    BusinessAuditService auditService = Mockito.mock(BusinessAuditService.class);
    AuditOperationHandle handle = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    service = new TagService(currentProject, tagMapper, tagRelMapper, itemMapper, auditService,
        Mockito.mock(io.yak.ops.business.asset.health.HealthRecomputeService.class));
  }

  @Test
  void createRejectsDuplicateCode() {
    when(tagMapper.selectCount(any())).thenReturn(1L);
    AssetException ex = assertThrows(AssetException.class,
        () -> service.create("core", "核心", null, null, "root"));
    assertEquals(AssetErrorCode.DUPLICATE_TAG_CODE, ex.getErrorCode());
    verify(tagMapper, never()).insert(any(AssetTagPO.class));
  }

  @Test
  void createAutoGeneratesCodeWhenBlank() {
    when(tagMapper.selectCount(any())).thenReturn(0L);
    TagService.TagView view = service.create(null, "核心", "red", null, "root");
    assertEquals("核心", view.tagName());
    ArgumentCaptor<AssetTagPO> captor = ArgumentCaptor.forClass(AssetTagPO.class);
    verify(tagMapper).insert(captor.capture());
    assertTrue(captor.getValue().getTagCode().startsWith("tag_"));
  }

  @Test
  void createLowerCasesGivenCode() {
    when(tagMapper.selectCount(any())).thenReturn(0L);
    TagService.TagView view = service.create(" Core ", "核心", null, null, "root");
    assertEquals("core", view.tagCode());
  }

  @Test
  void attachSkipsExistingRelationsIdempotent() {
    when(itemMapper.selectCount(any())).thenReturn(1L);
    when(tagMapper.selectOne(any())).thenReturn(tag(3L, "core"));
    when(tagRelMapper.selectCount(any())).thenReturn(1L);
    assertEquals(0, service.attach(1L, List.of(3L), "root"));
    verify(tagRelMapper, never()).insert(any(AssetTagRelPO.class));
  }

  @Test
  void attachInsertsMissingRelations() {
    when(itemMapper.selectCount(any())).thenReturn(1L);
    when(tagMapper.selectOne(any())).thenReturn(tag(3L, "core"));
    when(tagRelMapper.selectCount(any())).thenReturn(0L);
    assertEquals(1, service.attach(1L, List.of(3L), "root"));
    verify(tagRelMapper).insert(any(AssetTagRelPO.class));
  }

  @Test
  void attachRejectsUnknownAsset() {
    when(itemMapper.selectCount(any())).thenReturn(0L);
    AssetException ex = assertThrows(AssetException.class,
        () -> service.attach(404L, List.of(3L), "root"));
    assertEquals(AssetErrorCode.ASSET_NOT_FOUND, ex.getErrorCode());
  }

  @Test
  void deleteRemovesRelationsPhysically() {
    when(tagMapper.selectOne(any())).thenReturn(tag(3L, "core"));
    service.delete(3L, "root");
    verify(tagRelMapper).delete(any());
    verify(tagMapper).updateById(any(AssetTagPO.class));
  }

  private static AssetTagPO tag(Long id, String code) {
    AssetTagPO po = new AssetTagPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setTagCode(code);
    po.setTagName("核心");
    po.setDeleted(false);
    return po;
  }
}
