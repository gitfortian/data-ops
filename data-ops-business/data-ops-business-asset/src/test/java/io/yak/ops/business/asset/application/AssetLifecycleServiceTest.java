package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetLifecycleService.PrecheckResult;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 状态机单测(ticket 96):预检缺口/token、带风险上架、下架原因、忽略门槛。 */
class AssetLifecycleServiceTest {

  private AssetItemMapper itemMapper;
  private io.yak.ops.business.asset.health.HealthRecomputeService healthRecompute;
  private AssetLifecycleService service;

  @BeforeEach
  void setUp() {
    itemMapper = mock(AssetItemMapper.class);
    healthRecompute = mock(io.yak.ops.business.asset.health.HealthRecomputeService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    AuditOperationHandle handle = mock(AuditOperationHandle.class);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    service = new AssetLifecycleService(currentProject, itemMapper,
        new PrecheckTokenService(), auditService, healthRecompute);
  }

  // ---------- 预检 ----------

  @Test
  void precheckListsBlockingGapsAndDefaults() {
    when(itemMapper.selectList(any()))
        .thenReturn(List.of(item(1L, AssetStatus.PENDING.name(), false)));

    PrecheckResult result = service.precheck(List.of(1L), "root");

    assertFalse(result.allClear());
    assertEquals(1, result.items().size());
    var gaps = result.items().get(0).gaps();
    assertTrue(gaps.contains("OWNER_MISSING"));
    assertTrue(gaps.contains("DESCRIPTION_MISSING"));
    assertTrue(gaps.contains("DIRECTORY_MISSING"));
    assertEquals("root", result.items().get(0).defaults().get("owner"));
    assertEquals(300, result.expiresIn());
    assertNotNull(result.token());
  }

  @Test
  void precheckCompleteAssetIsClearWithAdvisoryOnly() {
    when(itemMapper.selectList(any())).thenReturn(List.of(readyItem()));
    PrecheckResult result = service.precheck(List.of(1L), "root");
    assertTrue(result.allClear());
    assertEquals(List.of("SECURITY_LEVEL_SUGGESTED"), result.items().get(0).advisories());
  }

  @Test
  void precheckRejectsNonPublishableState() {
    when(itemMapper.selectList(any()))
        .thenReturn(List.of(item(1L, AssetStatus.SOURCE_GONE.name(), true)));
    AssetException ex =
        assertThrows(AssetException.class, () -> service.precheck(List.of(1L), "root"));
    assertEquals(AssetErrorCode.ILLEGAL_STATE_OPERATION, ex.getErrorCode());
  }

  @Test
  void precheckRejectsUnknownIdsAtomically() {
    when(itemMapper.selectList(any())).thenReturn(List.of());
    AssetException ex =
        assertThrows(AssetException.class, () -> service.precheck(List.of(1L, 2L), "root"));
    assertEquals(AssetErrorCode.ASSET_NOT_FOUND, ex.getErrorCode());
  }

  // ---------- 上架 ----------

  @Test
  void publishWithFreshTokenSucceedsAndStampsListedAt() {
    AssetItemPO po = readyItem();
    when(itemMapper.selectList(any())).thenReturn(List.of(po));
    PrecheckResult pre = service.precheck(List.of(1L), "root");

    assertEquals(1, service.publish(List.of(1L), pre.token(), false, "root"));
    assertEquals(AssetStatus.PUBLISHED.name(), po.getStatus());
    assertNotNull(po.getFirstListedAt());
    assertEquals(po.getFirstListedAt(), po.getLastListedAt());
    verify(itemMapper).updateById(po);
    verify(healthRecompute).recomputeItems(1L, List.of(1L));
  }

  @Test
  void publishRejectsMissingOrTamperedToken() {
    AssetException missing = assertThrows(AssetException.class,
        () -> service.publish(List.of(1L), null, true, "root"));
    assertEquals(AssetErrorCode.PRECHECK_TOKEN_INVALID, missing.getErrorCode());
    AssetException garbage = assertThrows(AssetException.class,
        () -> service.publish(List.of(1L), "abc", true, "root"));
    assertEquals(AssetErrorCode.PRECHECK_TOKEN_INVALID, garbage.getErrorCode());
    verify(itemMapper, never()).updateById(any(AssetItemPO.class));
  }

  @Test
  void publishWithGapsNeedsRiskAcceptanceThenAuditsRiskItems() {
    AssetItemPO gap = item(1L, AssetStatus.PENDING.name(), false);
    when(itemMapper.selectList(any())).thenReturn(List.of(gap));
    PrecheckResult pre = service.precheck(List.of(1L), "root");

    AssetException ex = assertThrows(AssetException.class,
        () -> service.publish(List.of(1L), pre.token(), false, "root"));
    assertEquals(AssetErrorCode.PRECHECK_FAILED, ex.getErrorCode());
    assertTrue(ex.getUserMessage().contains("OWNER"));
    assertEquals(AssetStatus.PENDING.name(), gap.getStatus());

    assertEquals(1, service.publish(List.of(1L), pre.token(), true, "root"));
    assertEquals(AssetStatus.PUBLISHED.name(), gap.getStatus());
  }

  @Test
  void publishAllowsIgnoredAndOfflineAsRepublish() {
    AssetItemPO ignored = item(1L, AssetStatus.IGNORED.name(), true);
    AssetItemPO offline = item(2L, AssetStatus.OFFLINE.name(), true);
    when(itemMapper.selectList(any())).thenReturn(List.of(ignored, offline));
    PrecheckResult pre = service.precheck(List.of(1L, 2L), "root");
    assertEquals(2, service.publish(List.of(1L, 2L), pre.token(), false, "root"));
    assertNotNull(ignored.getFirstListedAt());
  }

  // ---------- 下架 ----------

  @Test
  void offlineRequiresReasonAndPublishedState() {
    AssetException reason = assertThrows(AssetException.class,
        () -> service.offline(List.of(1L), "  ", "root"));
    assertEquals(AssetErrorCode.OFFLINE_REASON_REQUIRED, reason.getErrorCode());

    AssetItemPO pending = item(1L, AssetStatus.PENDING.name(), true);
    when(itemMapper.selectList(any())).thenReturn(List.of(pending));
    AssetException state = assertThrows(AssetException.class,
        () -> service.offline(List.of(1L), "停用", "root"));
    assertEquals(AssetErrorCode.ILLEGAL_STATE_OPERATION, state.getErrorCode());
  }

  @Test
  void offlinePublishedPersistsReason() {
    AssetItemPO published = item(1L, AssetStatus.PUBLISHED.name(), true);
    when(itemMapper.selectList(any())).thenReturn(List.of(published));

    assertEquals(1, service.offline(List.of(1L), "口径重构", "root"));
    assertEquals(AssetStatus.OFFLINE.name(), published.getStatus());
    assertEquals("口径重构", published.getLastOfflineReason());
    assertNotNull(published.getLastOfflineAt());
  }

  // ---------- 忽略 ----------

  @Test
  void ignoreOnlyForPendingOrOffline() {
    AssetItemPO published = item(1L, AssetStatus.PUBLISHED.name(), true);
    when(itemMapper.selectList(any())).thenReturn(List.of(published));
    AssetException ex = assertThrows(AssetException.class,
        () -> service.ignore(List.of(1L), "root"));
    assertEquals(AssetErrorCode.NOT_IGNORABLE, ex.getErrorCode());

    AssetItemPO pending = item(2L, AssetStatus.PENDING.name(), true);
    when(itemMapper.selectList(any())).thenReturn(List.of(pending));
    assertEquals(1, service.ignore(List.of(2L), "root"));
    assertEquals(AssetStatus.IGNORED.name(), pending.getStatus());
    assertNull(pending.getLastOfflineReason());
  }

  // ---------- fixtures ----------

  private static AssetItemPO item(Long id, String status, boolean complete) {
    AssetItemPO po = new AssetItemPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setAssetKey("modeling:model:" + id);
    po.setStatus(status);
    po.setName("资产");
    po.setDeleted(false);
    if (complete) {
      po.setOwner("carol");
      po.setDescription("描述");
      po.setDirectoryId(3L);
    }
    return po;
  }

  private static AssetItemPO readyItem() {
    AssetItemPO po = item(1L, AssetStatus.PENDING.name(), true);
    po.setSecurityLevelCode(null); // 定级缺失仅建议,不阻断
    return po;
  }
}
