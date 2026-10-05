package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ItemQueryDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ManualRegisterDTO;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 台账服务单测(ticket 91):手工登记默认值、键生成、删除状态门槛、负责人变更。 */
class AssetAppServiceTest {

  private AssetItemMapper mapper;
  private CurrentProject currentProject;
  private AssetAppService service;

  @BeforeEach
  void setUp() {
    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
        new org.apache.ibatis.builder.MapperBuilderAssistant(
            new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
        AssetItemPO.class);
    mapper = Mockito.mock(AssetItemMapper.class);
    currentProject = Mockito.mock(CurrentProject.class);
    BusinessAuditService auditService = Mockito.mock(BusinessAuditService.class);
    AuditOperationHandle handle = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    service = new AssetAppService(currentProject, mapper, auditService);
  }

  @Test
  void registerManualAppliesDefaults() {
    when(mapper.selectOne(any())).thenReturn(null);
    ManualRegisterDTO dto = new ManualRegisterDTO();
    dto.setName("外部采购清单");
    AssetAppService.AssetView view = service.registerManual(dto, "root");

    assertEquals("PENDING", view.status());
    assertEquals("DOC", view.assetType());
    assertEquals("MANUAL", view.sourceType());
    assertEquals("root", view.owner());
    assertNotNull(view.assetKey());
    assertTrue(view.assetKey().startsWith("manual:"));
    assertEquals(0, view.viewCount30d());
    verify(mapper).insert(any(AssetItemPO.class));
  }

  @Test
  void assetDetailLookupAlwaysScopesTheIdToTheTrustedProject() {
    when(mapper.selectOne(any())).thenReturn(new AssetItemPO());
    ArgumentCaptor<Wrapper<AssetItemPO>> query = ArgumentCaptor.forClass(Wrapper.class);

    service.requireItem(42L);

    verify(currentProject).requireProjectId();
    verify(mapper).selectOne(query.capture());
    @SuppressWarnings("unchecked")
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetItemPO> wrapper =
        (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetItemPO>)
            query.getValue();
    assertTrue(wrapper.getSqlSegment().contains("project_id"));
    assertTrue(wrapper.getSqlSegment().contains("id"));
    assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
    assertTrue(wrapper.getParamNameValuePairs().containsValue(42L));
  }

  @Test
  void registerManualUsesGivenCodeAndTrims() {
    when(mapper.selectOne(any())).thenReturn(null);
    ManualRegisterDTO dto = new ManualRegisterDTO();
    dto.setName("周报模板");
    dto.setAssetCode(" Weekly ");
    dto.setAssetType("doc");
    ArgumentCaptor<AssetItemPO> captor = ArgumentCaptor.forClass(AssetItemPO.class);
    service.registerManual(dto, "root");
    verify(mapper).insert(captor.capture());
    assertEquals("manual:weekly", captor.getValue().getAssetKey());
    assertEquals("DOC", captor.getValue().getAssetType());
  }

  @Test
  void registerManualRejectsUnknownAssetType() {
    ManualRegisterDTO dto = new ManualRegisterDTO();
    dto.setName("x");
    dto.setAssetType("ROCKET");
    AssetException ex =
        assertThrows(AssetException.class, () -> service.registerManual(dto, "root"));
    assertEquals(AssetErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    verify(mapper, never()).insert(any(AssetItemPO.class));
  }

  @Test
  void registerManualRejectsDuplicateGivenCode() {
    when(mapper.selectOne(any())).thenReturn(item(9L, "manual:dup", "PENDING"));
    ManualRegisterDTO dto = new ManualRegisterDTO();
    dto.setName("x");
    dto.setAssetCode("dup");
    AssetException ex =
        assertThrows(AssetException.class, () -> service.registerManual(dto, "root"));
    assertEquals(AssetErrorCode.DUPLICATE_ASSET_KEY, ex.getErrorCode());
  }

  @Test
  void deleteBlockedUnlessOfflineOrGone() {
    when(mapper.selectOne(any())).thenReturn(item(1L, "manual:a1", AssetStatus.PUBLISHED.name()));
    AssetException ex = assertThrows(AssetException.class, () -> service.delete(1L, "root"));
    assertEquals(AssetErrorCode.ILLEGAL_STATE_OPERATION, ex.getErrorCode());
    verify(mapper, never()).updateById(any(AssetItemPO.class));
  }

  @Test
  void deleteSoftRemovesWhenOffline() {
    when(mapper.selectOne(any())).thenReturn(item(1L, "manual:a1", AssetStatus.OFFLINE.name()));
    service.delete(1L, "root");
    ArgumentCaptor<AssetItemPO> captor = ArgumentCaptor.forClass(AssetItemPO.class);
    verify(mapper).updateById(captor.capture());
    assertTrue(captor.getValue().getDeleted());
  }

  @Test
  void deleteManualPendingRevokesRegistration() {
    when(mapper.selectOne(any()))
        .thenReturn(item(1L, "manual:a1", AssetStatus.PENDING.name(), "MANUAL"));
    service.delete(1L, "root");
    ArgumentCaptor<AssetItemPO> captor = ArgumentCaptor.forClass(AssetItemPO.class);
    verify(mapper).updateById(captor.capture());
    assertTrue(captor.getValue().getDeleted());
  }

  @Test
  void deleteManualIgnoredRevokesRegistration() {
    when(mapper.selectOne(any()))
        .thenReturn(item(1L, "manual:a1", AssetStatus.IGNORED.name(), "MANUAL"));
    service.delete(1L, "root");
    verify(mapper).updateById(any(AssetItemPO.class));
  }

  @Test
  void deleteManualPublishedStillRequiresOffline() {
    when(mapper.selectOne(any()))
        .thenReturn(item(1L, "manual:a1", AssetStatus.PUBLISHED.name(), "MANUAL"));
    AssetException ex = assertThrows(AssetException.class, () -> service.delete(1L, "root"));
    assertEquals(AssetErrorCode.ILLEGAL_STATE_OPERATION, ex.getErrorCode());
    verify(mapper, never()).updateById(any(AssetItemPO.class));
  }

  @Test
  void deleteSourcedPendingStaysBlocked() {
    when(mapper.selectOne(any()))
        .thenReturn(item(1L, "table:3:crm_db.crm_customer", AssetStatus.PENDING.name(), "METADATA"));
    AssetException ex = assertThrows(AssetException.class, () -> service.delete(1L, "root"));
    assertEquals(AssetErrorCode.ILLEGAL_STATE_OPERATION, ex.getErrorCode());
    verify(mapper, never()).updateById(any(AssetItemPO.class));
  }

  @Test
  void changeOwnerPersistsTrimsAndAudits() {
    when(mapper.selectOne(any())).thenReturn(item(2L, "manual:a2", AssetStatus.PENDING.name()));
    AssetAppService.AssetView view = service.changeOwner(2L, " lucas ", "root");
    assertEquals("lucas", view.owner());
    verify(mapper).updateById(any(AssetItemPO.class));
  }

  @Test
  void getRejectsMissingAcrossProject() {
    when(mapper.selectOne(any())).thenReturn(null);
    AssetException ex = assertThrows(AssetException.class, () -> service.get(404L));
    assertEquals(AssetErrorCode.ASSET_NOT_FOUND, ex.getErrorCode());
  }

  @Test
  void parseAssetTypeDefaultsToDoc() {
    assertEquals("TABLE", AssetAppService.parseAssetType(" table "));
    assertEquals("DOC", AssetAppService.parseAssetType(null));
  }

  @Test
  void pageDefaultSortUsesHealthActivityFormula() {
    when(mapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus
        .extension.plugins.pagination.Page<>());
    service.page(new ItemQueryDTO());
    assertTrue(AssetAppService.DEFAULT_SCORE_ORDER.contains("LOG2(1+COALESCE(view_count_30d,0))"));
    assertTrue(AssetAppService.DEFAULT_SCORE_ORDER.startsWith("ORDER BY "));
  }

  @Test
  void pageAcceptsTimeViewsNameSortsAndRejectsUnknown() {
    when(mapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus
        .extension.plugins.pagination.Page<>());
    ItemQueryDTO q = new ItemQueryDTO();
    for (String sortBy : new String[] {"time", "VIEWS", " NAME "}) {
      q.setSortBy(sortBy);
      service.page(q);
    }
    q.setSortBy("HEALTH");
    AssetException ex = assertThrows(AssetException.class, () -> service.page(q));
    assertEquals(AssetErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
  }

  @Test
  void pageCombinesMultiFiltersAndTagSubquery() {
    when(mapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus
        .extension.plugins.pagination.Page<>());
    ItemQueryDTO q = new ItemQueryDTO();
    q.setStatuses(java.util.List.of("PUBLISHED", "PENDING"));
    q.setAssetTypes(java.util.List.of("table"));
    q.setLayerCodes(java.util.List.of("dwd"));
    q.setGrades(java.util.List.of("A"));
    q.setTagIds(java.util.Arrays.asList(7L, 8L, 7L, null));
    service.page(q);

    ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AssetItemPO>> captor =
        ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
    verify(mapper).selectPage(any(), captor.capture());
    @SuppressWarnings("unchecked")
    var wrapper = (com.baomidou.mybatisplus.core.conditions.query
        .LambdaQueryWrapper<AssetItemPO>) captor.getValue();
    String sql = wrapper.getTargetSql();
    assertTrue(sql.contains("status IN"), sql);
    assertTrue(sql.contains("tag_id IN (7,8)"), sql);
    assertTrue(sql.contains("yak_asset_tag_rel"), sql);
    assertTrue(wrapper.getParamNameValuePairs().containsValue("DWD"),
        String.valueOf(wrapper.getParamNameValuePairs()));
    assertTrue(wrapper.getParamNameValuePairs().containsValue("TABLE"));
    assertTrue(wrapper.getParamNameValuePairs().containsValue("A"));
  }

  private static AssetItemPO item(Long id, String key, String status) {
    return item(id, key, status, "MANUAL");
  }

  @Test
  void conditionalSnapshotRejectsStaleDescriptionBeforeWriting() {
    var po = item(7L, "manual:7", "PENDING");
    po.setDescription("another editor saved");
    when(mapper.selectOne(any())).thenReturn(po);
    String old = AssetSnapshotFingerprint.of(7L, po.getName(), "old description", null);
    assertThrows(AssetException.class, () -> service.updateSnapshot(7L, null, "AI draft", null, "actor", old));
    verify(mapper, never()).update(any(), any());
    verify(mapper, never()).updateById(any(AssetItemPO.class));
    assertEquals("another editor saved", po.getDescription());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void conditionalSnapshotLocksAndUpdatesOnlyEditableFieldsWithinProject() {
    var po = item(7L, "manual:7", "PENDING");
    po.setOwner("current owner");
    when(mapper.selectOne(any())).thenReturn(po);
    when(mapper.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class))).thenReturn(1);
    String definition = AssetSnapshotFingerprint.of(7L, po.getName(), po.getDescription(), po.getAccessUri());
    var result = service.updateSnapshot(7L, null, "AI draft", null, "actor", definition);
    assertEquals("资产", result.name());
    assertEquals("current owner", result.owner());
    var read = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectOne(read.capture());
    assertTrue(read.getValue().getSqlSegment().contains("FOR UPDATE"));
    var write = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).update(org.mockito.ArgumentMatchers.isNull(), write.capture());
    var condition = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AssetItemPO>) write.getValue();
    assertTrue(condition.getSqlSegment().contains("project_id"));
    assertTrue(condition.getSqlSegment().contains("description IS NULL"));
    assertTrue(condition.getSqlSet().contains("description="));
    assertTrue(!condition.getSqlSet().contains("owner=") && !condition.getSqlSet().contains("status="));
    verify(mapper, never()).updateById(any(AssetItemPO.class));
  }

  private static AssetItemPO item(Long id, String key, String status, String sourceType) {
    AssetItemPO po = new AssetItemPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setAssetKey(key);
    po.setSourceType(sourceType);
    po.setSourceId(key.substring(key.indexOf(':') + 1));
    po.setAssetType("DOC");
    po.setName("资产");
    po.setStatus(status);
    po.setDeleted(false);
    po.setViewCount30d(0);
    return po;
  }
}
