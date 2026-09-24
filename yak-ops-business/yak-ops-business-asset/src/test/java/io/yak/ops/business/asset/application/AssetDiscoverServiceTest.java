package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import io.yak.ops.business.asset.reconcile.AssetProviderRegistry;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 360° 详情聚合单测(ticket 97):本体必成、每分区独立容错、不伪造。 */
class AssetDiscoverServiceTest {

  private AssetAppService assetAppService;
  private AssetProviderRegistry registry;
  private AssetViewRecordService viewRecordService;
  private AssetStatusFlowService statusFlowService;
  private ObjectProvider<LineageQueryService> lineageProvider;
  private ObjectProvider<io.yak.framework.security.service.RbacPermissionService> rbacProvider;
  private ObjectProvider<SectionProvider> sectionProviders;
  private io.yak.framework.security.service.RbacPermissionService permissionService;
  private AssetDiscoverService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    assetAppService = mock(AssetAppService.class);
    registry = mock(AssetProviderRegistry.class);
    viewRecordService = mock(AssetViewRecordService.class);
    statusFlowService = mock(AssetStatusFlowService.class);
    when(statusFlowService.flow(any())).thenReturn(new AssetStatusFlowService.StatusFlowView(
        List.of(new AssetStatusFlowService.StatusStep(
            "discovered", "已发现", "PASS", null, Map.of())), "described"));
    lineageProvider = mock(ObjectProvider.class);
    rbacProvider = mock(ObjectProvider.class);
    permissionService = mock(io.yak.framework.security.service.RbacPermissionService.class);
    when(permissionService.hasPermission(anyString(), anyString())).thenReturn(true);
    when(rbacProvider.orderedStream()).thenAnswer(invocation -> Stream.of(permissionService));
    sectionProviders = mock(ObjectProvider.class);
    when(sectionProviders.orderedStream()).thenAnswer(invocation -> Stream.empty());
    service = new AssetDiscoverService(assetAppService, registry, viewRecordService,
        statusFlowService, lineageProvider, rbacProvider, sectionProviders);
    when(viewRecordService.trend(anyLong(), anyInt()))
        .thenReturn(List.of(new AssetViewRecordService.DailyView("2026-09-19", 2)));
  }

  @Test
  void missingExtensionsDegradePerSectionWithoutFailingDetail() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());

    AssetDiscoverService.AssetDetailView detail = service.detail(1L, "alice");

    assertEquals(1L, detail.asset().id());
    assertEquals(SectionStatus.OK, section(detail, "statusFlow").status());
    assertEquals(SectionStatus.OK, section(detail, "trend").status());
    assertEquals(3, detail.sections().size());
    verify(registry, never()).find(any());
    verify(lineageProvider, never()).getIfAvailable();
  }

  @Test
  void sourceAttrsFlagChangedWhenFingerprintDrifts() {
    AssetItemPO po = modelItem();
    when(assetAppService.requireItem(1L)).thenReturn(po);
    AssetProvider provider = mock(AssetProvider.class);
    when(provider.refresh("42")).thenReturn(Optional.of(descriptor("other-hash")));
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.of(provider));

    AssetDiscoverService.SectionView sourceAttrs = service.sourceAttributes(1L, "alice");

    assertEquals(SectionStatus.OK, sourceAttrs.status());
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) sourceAttrs.data();
    assertEquals(Boolean.TRUE, data.get("sourceChanged"));
    assertEquals("订单模型", data.get("name"));
  }

  @Test
  void providerFailureIsContainedToSourceAttrs() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    AssetProvider provider = mock(AssetProvider.class);
    when(provider.refresh(anyString())).thenThrow(new RuntimeException("db down"));
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.of(provider));

    AssetDiscoverService.SectionView sourceAttrs = service.sourceAttributes(1L, "alice");
    assertEquals(SectionStatus.UNAVAILABLE, sourceAttrs.status());
    assertEquals("源域暂不可用，请稍后重试", sourceAttrs.note());
  }

  @Test
  void lineageResolvesAssetKeyThenReadsOneHopGraph() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    LineageQueryService lineage = mock(LineageQueryService.class);
    LineageAsset root = mock(LineageAsset.class);
    when(root.id()).thenReturn(7L);
    when(lineage.findAssetByKey("modeling:model:42")).thenReturn(Optional.of(root));
    when(lineage.graph(7L, LineageDirection.BOTH, 1)).thenReturn(null);
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    assertEquals(SectionStatus.OK, service.section(1L, "LINEAGE", "alice").status());
  }

  @Test
  void lineageKeyWithoutRegistrationIsEmpty() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    LineageQueryService lineage = mock(LineageQueryService.class);
    when(lineage.findAssetByKey(anyString())).thenReturn(Optional.empty());
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    AssetDiscoverService.SectionView result = service.section(1L, "LINEAGE", "alice");
    assertEquals(SectionStatus.EMPTY, result.status());
    assertEquals("该资产尚未登记血缘", result.note());
  }

  @Test
  void lineageQueryFailureRemainsUnavailable() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    LineageQueryService lineage = mock(LineageQueryService.class);
    when(lineage.findAssetByKey(anyString())).thenThrow(new IllegalStateException("database down"));
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    AssetDiscoverService.SectionView result = service.section(1L, "LINEAGE", "alice");
    assertEquals(SectionStatus.UNAVAILABLE, result.status());
    assertEquals("血缘查询暂不可用，请稍后重试", result.note());
  }

  @Test
  void invalidLineageKeyRemainsUnavailable() {
    AssetItemPO item = modelItem();
    item.setAssetKey(" ");
    when(assetAppService.requireItem(1L)).thenReturn(item);
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    LineageQueryService lineage = mock(LineageQueryService.class);
    when(lineage.findAssetByKey(anyString()))
        .thenThrow(new IllegalArgumentException("assetKey 不能为空"));
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    AssetDiscoverService.SectionView result = service.section(1L, "LINEAGE", "alice");
    assertEquals(SectionStatus.UNAVAILABLE, result.status());
  }

  @Test
  void securityUsesLiveClassificationWhenKeyMatches() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    SectionProvider provider = mock(SectionProvider.class);
    SectionContract contract = mock(SectionContract.class);
    when(provider.sectionType()).thenReturn(SectionType.SECURITY);
    when(provider.supports(any())).thenReturn(true);
    when(provider.query(any())).thenReturn(contract);
    when(contract.status()).thenReturn(SectionStatus.OK);
    when(sectionProviders.orderedStream()).thenAnswer(invocation -> Stream.of(provider));
    AssetDiscoverService.SectionView security = service.section(1L, "SECURITY", "alice");
    assertEquals(SectionStatus.OK, security.status());
    assertEquals(contract, security.data());
  }

  @Test
  void manualAssetHasNoSourceDomain() {
    AssetItemPO po = modelItem();
    po.setSourceType(AssetSourceType.MANUAL.name());
    when(assetAppService.requireItem(1L)).thenReturn(po);
    when(lineageProvider.getIfAvailable()).thenReturn(null);
    AssetDiscoverService.SectionView sourceAttrs = service.sourceAttributes(1L, "alice");
    assertEquals(SectionStatus.UNAVAILABLE, sourceAttrs.status());
    assertTrue(sourceAttrs.note().contains("手工登记"));
    assertNull(sourceAttrs.data());
  }

  @Test
  void modelAssetWithTableDisplayTypeDoesNotQueryPhysicalMetadata() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());

    AssetDiscoverService.SectionView result = service.section(1L, "TECHNICAL_METADATA", "alice");

    assertEquals(SectionStatus.NOT_APPLICABLE, result.status());
    assertNull(result.data());
    verify(sectionProviders, never()).orderedStream();
    verify(registry, never()).find(AssetSourceType.MODEL);
    verify(lineageProvider, never()).getIfAvailable();
  }

  @Test
  void modelAssetQualityIsNotApplicableEvenWhenDisplayTypeIsTable() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());

    AssetDiscoverService.SectionView result = service.section(1L, "QUALITY", "alice");

    assertEquals(SectionStatus.NOT_APPLICABLE, result.status());
    assertNull(result.data());
    verify(sectionProviders, never()).orderedStream();
  }

  @Test
  void modelSourceAttributesRequireModelOwnerPermission() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(permissionService.hasPermission("alice", "modeling:read")).thenReturn(false);
    when(permissionService.hasPermission("alice", "data-metadata:read")).thenReturn(true);

    AssetDiscoverService.SectionView result = service.sourceAttributes(1L, "alice");

    assertEquals(SectionStatus.PERMISSION_DENIED, result.status());
    verify(permissionService).hasPermission("alice", "modeling:read");
  }

  @Test
  void physicalMetadataWithoutItsSectionProviderIsUnavailable() {
    AssetItemPO physicalTable = modelItem();
    physicalTable.setSourceType(AssetSourceType.METADATA.name());
    when(assetAppService.requireItem(1L)).thenReturn(physicalTable);

    AssetDiscoverService.SectionView result = service.section(1L, "TECHNICAL_METADATA", "alice");

    assertEquals(SectionStatus.UNAVAILABLE, result.status());
    assertEquals("TECHNICAL_METADATA 分区读取提供方未装配或暂不可用", result.note());
    assertNull(result.data());
    verify(registry, never()).find(AssetSourceType.METADATA);
  }

  @Test
  void qualityWithoutAnAssembledProviderIsUnavailable() {
    AssetItemPO physicalTable = modelItem();
    physicalTable.setSourceType(AssetSourceType.METADATA.name());
    when(assetAppService.requireItem(1L)).thenReturn(physicalTable);
    when(registry.find(AssetSourceType.METADATA)).thenReturn(Optional.of(metadataProvider()));

    AssetDiscoverService.SectionView result = service.section(1L, "QUALITY", "alice");

    assertEquals(SectionStatus.UNAVAILABLE, result.status());
    assertEquals("QUALITY 分区读取提供方未装配或暂不可用", result.note());
    verify(sectionProviders).orderedStream();
  }

  @Test
  void qualitySectionPassesThroughEachOfTheFiveContractStates() {
    AssetItemPO physicalTable = modelItem();
    physicalTable.setSourceType(AssetSourceType.METADATA.name());
    when(assetAppService.requireItem(1L)).thenReturn(physicalTable);
    when(registry.find(AssetSourceType.METADATA)).thenReturn(Optional.of(metadataProvider()));

    for (SectionStatus status : SectionStatus.values()) {
      SectionProvider provider = mock(SectionProvider.class);
      when(provider.sectionType()).thenReturn(SectionType.QUALITY);
      when(provider.supports(any())).thenReturn(true);
      when(provider.query(any())).thenReturn(contract(status));
      when(sectionProviders.orderedStream()).thenAnswer(invocation -> Stream.of(provider));

      AssetDiscoverService.SectionView result = service.section(1L, "QUALITY", "alice");

      assertEquals(status, result.status());
      verify(provider).query(any());
    }
  }

  @Test
  void deniedQualitySectionDoesNotEnumerateOrCallProvider() {
    AssetItemPO physicalTable = modelItem();
    physicalTable.setSourceType(AssetSourceType.METADATA.name());
    when(assetAppService.requireItem(1L)).thenReturn(physicalTable);
    when(permissionService.hasPermission("alice", "quality:monitor:read")).thenReturn(false);

    AssetDiscoverService.SectionView result = service.section(1L, "QUALITY", "alice");

    assertEquals(SectionStatus.PERMISSION_DENIED, result.status());
    assertNull(result.data());
    verify(sectionProviders, never()).orderedStream();
    verify(registry, never()).find(AssetSourceType.METADATA);
  }

  @Test
  void qualitySectionRequiresExecutionReadPermissionAsWellAsMonitorRead() {
    AssetItemPO physicalTable = modelItem();
    physicalTable.setSourceType(AssetSourceType.METADATA.name());
    when(assetAppService.requireItem(1L)).thenReturn(physicalTable);
    when(permissionService.hasPermission("alice", "quality:execution:read")).thenReturn(false);

    AssetDiscoverService.SectionView result = service.section(1L, "QUALITY", "alice");

    assertEquals(SectionStatus.PERMISSION_DENIED, result.status());
    assertNull(result.data());
    verify(permissionService).hasPermission("alice", "quality:monitor:read");
    verify(permissionService).hasPermission("alice", "quality:execution:read");
    verify(sectionProviders, never()).orderedStream();
    verify(registry, never()).find(AssetSourceType.METADATA);
  }

  private AssetProvider metadataProvider() {
    return new AssetProvider() {
      @Override public AssetSourceType sourceType() { return AssetSourceType.METADATA; }
      @Override public io.yak.ops.business.asset.api.AssetPage cursorList(
          io.yak.ops.business.asset.api.AssetCursorQuery query) { return null; }
      @Override public Optional<AssetDescriptor> refresh(String sourceId) {
        return Optional.of(new AssetDescriptor("metadata:table:42", sourceId, "orders", "",
            AssetType.TABLE, null, null, "alice", LocalDateTime.now(), "hash",
            Map.of("dataSourceId", "7", "databaseName", "sales", "tableName", "orders")));
      }
    };
  }

  private static SectionContract contract(SectionStatus status) {
    return new SectionContract() {
      @Override public SectionType sectionType() { return SectionType.QUALITY; }
      @Override public SectionStatus status() { return status; }
      @Override public String ownerDomain() { return "QUALITY"; }
      @Override public io.yak.ops.spi.section.SectionSummary summary() { return () -> Map.of(); }
      @Override public String reason() { return status == SectionStatus.OK ? null : "reason"; }
      @Override public java.time.Instant updatedAt() { return null; }
      @Override public List<io.yak.ops.spi.section.SectionAction> actions() { return List.of(); }
      @Override public List<io.yak.ops.spi.section.SectionEvidence> evidence() { return List.of(); }
      @Override public io.yak.ops.spi.section.SectionProvenance provenance() {
        return new io.yak.ops.spi.section.SectionProvenance("QUALITY", "test", null);
      }
      @Override public io.yak.ops.spi.section.SectionCapability capability() {
        return new io.yak.ops.spi.section.SectionCapability(true, false, null);
      }
    };
  }

  @Test
  void deniedSecuritySectionReturnsNoSummary() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(permissionService.hasPermission("alice", "data-security:read")).thenReturn(false);

    AssetDiscoverService.SectionView result = service.section(1L, "SECURITY", "alice");

    assertEquals(SectionStatus.PERMISSION_DENIED, result.status());
    assertNull(result.data());
  }

  @Test
  void unsupportedQualitySectionIsExplicitlyNotApplicable() {
    AssetItemPO metric = modelItem();
    metric.setAssetType(AssetType.METRIC.name());
    when(assetAppService.requireItem(1L)).thenReturn(metric);

    AssetDiscoverService.SectionView result = service.section(1L, "QUALITY", "alice");

    assertEquals(SectionStatus.NOT_APPLICABLE, result.status());
    assertNull(result.data());
  }

  // ---------- fixtures ----------

  private static AssetDiscoverService.SectionView section(
      AssetDiscoverService.AssetDetailView detail, String name) {
    AssetDiscoverService.SectionView view = detail.sections().get(name);
    assertNotNull(view, name);
    return view;
  }

  private static AssetItemPO modelItem() {
    AssetItemPO po = new AssetItemPO();
    po.setId(1L);
    po.setProjectId(1L);
    po.setAssetKey("modeling:model:42");
    po.setSourceType(AssetSourceType.MODEL.name());
    po.setSourceId("42");
    po.setAssetType(AssetType.TABLE.name());
    po.setName("订单模型");
    po.setStatus("PUBLISHED");
    po.setContentHash("hash-1");
    po.setViewCount30d(0);
    po.setDeleted(false);
    return po;
  }

  private static AssetDescriptor descriptor(String hash) {
    return new AssetDescriptor("modeling:model:42", "42", "订单模型", "描述",
        AssetType.TABLE, "DWD", null, "alice", LocalDateTime.now(), hash,
        Map.of("modelCode", "dwd_order"));
  }
}
