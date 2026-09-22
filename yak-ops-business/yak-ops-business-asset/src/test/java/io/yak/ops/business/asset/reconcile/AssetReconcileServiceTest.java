package io.yak.ops.business.asset.reconcile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.asset.application.AssetSettingService;
import io.yak.ops.business.asset.catalog.AssignRuleService;
import io.yak.ops.business.asset.dao.mapper.AssetChangeRecordMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.po.asset.AssetChangeRecordPO;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetEnums.ChangeType;
import io.yak.ops.common.enums.asset.AssetEnums.HandleStatus;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 对账引擎单测:upsert 三分支、IGNORED 抑制、SOURCE_GONE/REAPPEARED、provider 容错、入口校验。 */
class AssetReconcileServiceTest {

  private AssetProviderRegistry registry;
  private AssetItemMapper itemMapper;
  private AssetChangeRecordMapper changeMapper;
  private AssetTagRelMapper tagRelMapper;
  private AssignRuleService ruleService;
  private AssetSettingService settingService;
  private AssetReconcileService service;

  @BeforeEach
  void setUp() {
    registry = mock(AssetProviderRegistry.class);
    itemMapper = Mockito.mock(AssetItemMapper.class);
    changeMapper = Mockito.mock(AssetChangeRecordMapper.class);
    tagRelMapper = Mockito.mock(AssetTagRelMapper.class);
    ruleService = mock(AssignRuleService.class);
    settingService = mock(AssetSettingService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    io.yak.ops.business.audit.AuditOperationHandle handle =
        mock(io.yak.ops.business.audit.AuditOperationHandle.class);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    service = new AssetReconcileService(currentProject, registry, itemMapper, changeMapper,
        tagRelMapper, ruleService, settingService, auditService,
        mock(io.yak.ops.business.asset.schedule.AssetScheduleEngineBridge.class),
        mock(io.yak.ops.business.asset.health.HealthRecomputeService.class));
  }

  // ---------- upsert 三分支 ----------

  @Test
  void newDescriptorCreatesPendingWithRuleSuggestions() {
    when(itemMapper.selectOne(any())).thenReturn(null);
    when(ruleService.suggestedDirectoryId(eq(1L), any())).thenReturn(5L);
    when(ruleService.suggestedTagIds(eq(1L), any())).thenReturn(List.of(7L));

    service.upsert(1L, AssetSourceType.MODEL, descriptor("h1"), LocalDateTime.now(), "rec");

    ArgumentCaptor<AssetItemPO> item = ArgumentCaptor.forClass(AssetItemPO.class);
    verify(itemMapper).insert(item.capture());
    AssetItemPO po = item.getValue();
    assertEquals(AssetStatus.PENDING.name(), po.getStatus());
    assertEquals("carol", po.getOwner());
    assertEquals(5L, po.getDirectoryId());
    assertEquals("h1", po.getContentHash());
    assertEquals("modeling:model:42", po.getAssetKey());
    verify(tagRelMapper).insert(any(io.yak.ops.common.bean.po.asset.AssetTagRelPO.class));
    AssetChangeRecordPO change = capturedChange();
    assertEquals(ChangeType.NEW.name(), change.getChangeType());
    assertEquals(HandleStatus.OPEN.name(), change.getHandleStatus());
  }

  @Test
  void hashChangeWritesMetaChangedWithoutOverwritingDisplayFields() {
    AssetItemPO existing = item(10L, AssetStatus.PUBLISHED.name(), "h1");
    existing.setName("台账名");
    when(itemMapper.selectOne(any())).thenReturn(existing);

    service.upsert(1L, AssetSourceType.MODEL, descriptor("h2"), LocalDateTime.now(), "rec");

    ArgumentCaptor<AssetItemPO> updated = ArgumentCaptor.forClass(AssetItemPO.class);
    verify(itemMapper).updateById(updated.capture());
    assertEquals("台账名", updated.getValue().getName());
    assertEquals("h2", updated.getValue().getContentHash());
    assertEquals(AssetStatus.PUBLISHED.name(), updated.getValue().getStatus());
    AssetChangeRecordPO change = capturedChange();
    assertEquals(ChangeType.META_CHANGED.name(), change.getChangeType());
    assertTrue(change.getDiff().contains("name"));
    assertNotNull(change.getSnapshotNew());
    verify(itemMapper, never()).insert(any(AssetItemPO.class));
  }

  @Test
  void sameHashOnlyRefreshesReconciledAt() {
    AssetItemPO existing = item(10L, AssetStatus.PUBLISHED.name(), "h1");
    when(itemMapper.selectOne(any())).thenReturn(existing);

    service.upsert(1L, AssetSourceType.MODEL, descriptor("h1"), LocalDateTime.now(), "rec");

    verify(itemMapper).updateById(any(AssetItemPO.class));
    verify(changeMapper, never()).insert(any(AssetChangeRecordPO.class));
  }

  @Test
  void ignoredStaysIgnoredButReturnsPendingOnHashChange() {
    AssetItemPO ignored = item(10L, AssetStatus.IGNORED.name(), "h1");
    when(itemMapper.selectOne(any())).thenReturn(ignored);
    service.upsert(1L, AssetSourceType.MODEL, descriptor("h1"), LocalDateTime.now(), "rec");
    assertEquals(AssetStatus.IGNORED.name(), ignored.getStatus());
    verify(changeMapper, never()).insert(any(AssetChangeRecordPO.class));

    service.upsert(1L, AssetSourceType.MODEL, descriptor("h2"), LocalDateTime.now(), "rec");
    assertEquals(AssetStatus.PENDING.name(), ignored.getStatus());
    AssetChangeRecordPO change = capturedChange();
    assertEquals(ChangeType.NEW.name(), change.getChangeType());
  }

  @Test
  void sourceGoneRestoresPreviousStatusAndRecordsReappeared() {
    AssetItemPO gone = item(10L, AssetStatus.SOURCE_GONE.name(), "h1");
    when(itemMapper.selectOne(any())).thenReturn(gone);
    AssetChangeRecordPO goneRecord = new AssetChangeRecordPO();
    goneRecord.setDiff("{\"previousStatus\":\"PUBLISHED\"}");
    when(changeMapper.selectOne(any())).thenReturn(goneRecord);

    service.upsert(1L, AssetSourceType.MODEL, descriptor("h1"), LocalDateTime.now(), "rec");

    assertEquals(AssetStatus.PUBLISHED.name(), gone.getStatus());
    AssetChangeRecordPO change = capturedChange();
    assertEquals(ChangeType.REAPPEARED.name(), change.getChangeType());
  }

  // ---------- SOURCE_GONE 窗口 ----------

  @Test
  void markGoneOnlyScansSucceededTypesAndWindow() {
    when(settingService.getInt(1L, AssetReconcileService.SETTING_GONE_WINDOW_DAYS, 7))
        .thenReturn(7);
    AssetItemPO stale = item(11L, AssetStatus.PUBLISHED.name(), "h");
    when(itemMapper.selectList(any())).thenReturn(List.of(stale));

    int gone = service.markGoneAssets(1L, List.of(AssetSourceType.MODEL),
        LocalDateTime.now());

    assertEquals(1, gone);
    assertEquals(AssetStatus.SOURCE_GONE.name(), stale.getStatus());
    AssetChangeRecordPO change = capturedChange();
    assertEquals(ChangeType.SOURCE_GONE.name(), change.getChangeType());
    assertTrue(change.getDiff().contains("PUBLISHED"));
  }

  @Test
  void markGoneSkippedWhenNoProviderSucceeded() {
    assertEquals(0, service.markGoneAssets(1L, List.of(), LocalDateTime.now()));
    verify(itemMapper, never()).selectList(any());
  }

  // ---------- 编排 ----------

  @Test
  void providerFailureIsIsolatedAndVisible() {
    AssetProvider failing = mock(AssetProvider.class);
    when(failing.cursorList(any())).thenThrow(new RuntimeException("datasource down"));
    AssetProvider ok = mock(AssetProvider.class);
    when(ok.cursorList(any())).thenReturn(AssetPage.empty());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.of(failing));
    when(registry.find(AssetSourceType.METRIC)).thenReturn(Optional.of(ok));
    when(itemMapper.selectList(any())).thenReturn(List.of());

    List<AssetReconcileService.ProviderOutcome> outcomes = service.reconcileNow(1L,
        List.of(AssetSourceType.MODEL, AssetSourceType.METRIC), "rec", true);

    assertEquals(2, outcomes.size());
    assertEquals("datasource down", outcomes.get(0).error());
    assertNull(outcomes.get(1).error());
    verify(settingService).put(eq(1L),
        eq(AssetReconcileService.SETTING_LAST_PREFIX + "MODEL"), anyString());
  }

  @Test
  void submitRejectsManualMissingProviderAndEmptyRegistry() {
    when(registry.registeredTypes()).thenReturn(List.of(AssetSourceType.MODEL));
    AssetException manual = assertThrows(AssetException.class,
        () -> service.submit(List.of("MANUAL"), "rec"));
    assertEquals(AssetErrorCode.MANUAL_NOT_RECONCILABLE, manual.getErrorCode());

    AssetException missing = assertThrows(AssetException.class,
        () -> service.submit(List.of("DATASET"), "rec"));
    assertEquals(AssetErrorCode.PROVIDER_UNAVAILABLE, missing.getErrorCode());

    when(registry.registeredTypes()).thenReturn(List.of());
    AssetException none = assertThrows(AssetException.class, () -> service.submit(List.of(), "rec"));
    assertEquals(AssetErrorCode.PROVIDER_UNAVAILABLE, none.getErrorCode());
  }

  @Test
  void cursorLoopPagesUntilExhausted() {
    AssetProvider provider = mock(AssetProvider.class);
    when(provider.cursorList(any())).thenReturn(
        new AssetPage(List.of(descriptor("h1")), "2"),
        AssetPage.empty());
    when(itemMapper.selectOne(any())).thenReturn(item(10L, AssetStatus.PUBLISHED.name(), "h1"));
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.of(provider));
    when(itemMapper.selectList(any())).thenReturn(List.of());

    List<AssetReconcileService.ProviderOutcome> outcomes =
        service.reconcileNow(1L, List.of(AssetSourceType.MODEL), "rec", true);

    assertEquals(1, outcomes.get(0).scanned());
    verify(provider, org.mockito.Mockito.times(2)).cursorList(any());
    verify(itemMapper).updateById(any(AssetItemPO.class));
    verify(itemMapper, never()).insert(any(AssetItemPO.class));
  }

  // ---------- 变更确认 / 忽略(ticket 96) ----------

  @Test
  void confirmChangeAppliesSnapshotFieldsToLedger() {
    AssetChangeRecordPO open = changeRecord(HandleStatus.OPEN);
    when(changeMapper.selectOne(any())).thenReturn(open);
    AssetItemPO existing = item(10L, AssetStatus.PUBLISHED.name(), "h2");
    when(itemMapper.selectOne(any())).thenReturn(existing);

    service.confirmChange(66L, "root");

    assertEquals("订单模型", existing.getName());
    assertEquals("新描述", existing.getDescription());
    assertEquals(HandleStatus.CONFIRMED.name(), open.getHandleStatus());
    assertEquals("root", open.getHandledBy());
    assertNotNull(open.getHandledAt());
    verify(changeMapper).updateById(open);
    verify(itemMapper).updateById(existing);
  }

  @Test
  void confirmChangeWithoutSnapshotFallsBackToDiffNewSide() {
    AssetChangeRecordPO open = changeRecord(HandleStatus.OPEN);
    open.setSnapshotNew(null);
    open.setDiff("{\"name\":[\"旧名\",\"回Diff名\"]}");
    when(changeMapper.selectOne(any())).thenReturn(open);
    when(itemMapper.selectOne(any())).thenReturn(item(10L, AssetStatus.PUBLISHED.name(), "h2"));

    service.confirmChange(66L, "root");

    ArgumentCaptor<AssetItemPO> updated = ArgumentCaptor.forClass(AssetItemPO.class);
    verify(itemMapper).updateById(updated.capture());
    assertEquals("回Diff名", updated.getValue().getName());
  }

  @Test
  void confirmOrIgnoreRejectsHandledAndMissingRecords() {
    when(changeMapper.selectOne(any())).thenReturn(changeRecord(HandleStatus.CONFIRMED));
    AssetException handled =
        assertThrows(AssetException.class, () -> service.confirmChange(66L, "root"));
    assertEquals(AssetErrorCode.CHANGE_ALREADY_HANDLED, handled.getErrorCode());

    AssetException ignoreHandled =
        assertThrows(AssetException.class, () -> service.ignoreChange(66L, "root"));
    assertEquals(AssetErrorCode.CHANGE_ALREADY_HANDLED, ignoreHandled.getErrorCode());

    when(changeMapper.selectOne(any())).thenReturn(null);
    AssetException missing =
        assertThrows(AssetException.class, () -> service.confirmChange(404L, "root"));
    assertEquals(AssetErrorCode.ASSET_NOT_FOUND, missing.getErrorCode());
  }

  @Test
  void ignoreChangeClosesRecordWithoutTouchingLedger() {
    AssetChangeRecordPO open = changeRecord(HandleStatus.OPEN);
    when(changeMapper.selectOne(any())).thenReturn(open);

    service.ignoreChange(66L, "root");

    assertEquals(HandleStatus.IGNORED.name(), open.getHandleStatus());
    verify(changeMapper).updateById(open);
    verify(itemMapper, never()).updateById(any(AssetItemPO.class));
  }

  // ---------- fixtures ----------

  private static AssetChangeRecordPO changeRecord(HandleStatus status) {
    AssetChangeRecordPO po = new AssetChangeRecordPO();
    po.setId(66L);
    po.setProjectId(1L);
    po.setAssetId(10L);
    po.setChangeType(ChangeType.META_CHANGED.name());
    po.setHandleStatus(status.name());
    po.setSnapshotNew("{\"name\":\"订单模型\",\"description\":\"新描述\"}");
    po.setDeleted(false);
    return po;
  }

  private AssetChangeRecordPO capturedChange() {
    ArgumentCaptor<AssetChangeRecordPO> captor =
        ArgumentCaptor.forClass(AssetChangeRecordPO.class);
    verify(changeMapper, Mockito.atLeastOnce()).insert(captor.capture());
    List<AssetChangeRecordPO> allValues = captor.getAllValues();
    return allValues.get(allValues.size() - 1);
  }

  private static AssetDescriptor descriptor(String hash) {
    return new AssetDescriptor("modeling:model:42", "42", "订单模型", "描述",
        AssetType.TABLE, "DWD", null, "carol", LocalDateTime.now(), hash, Map.of());
  }

  private static AssetItemPO item(Long id, String status, String hash) {
    AssetItemPO po = new AssetItemPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setAssetKey("modeling:model:42");
    po.setSourceType(AssetSourceType.MODEL.name());
    po.setStatus(status);
    po.setContentHash(hash);
    po.setName("模型");
    po.setDeleted(false);
    return po;
  }
}
