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
import io.yak.ops.business.asset.reconcile.AssetProviderRegistry;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
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
  private ObjectProvider<SecurityClassificationQueryApi> securityProvider;
  private ObjectProvider<io.yak.framework.security.service.RbacPermissionService> rbacProvider;
  private ObjectProvider<io.yak.ops.business.quality.monitor.QualityMonitorReader> qualityProvider;
  private ObjectProvider<io.yak.ops.business.asset.api.AssetStatusModelFacts> modelFactsProvider;
  private ObjectProvider<io.yak.ops.business.asset.api.AssetStatusTtlFacts> ttlFactsProvider;
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
    securityProvider = mock(ObjectProvider.class);
    rbacProvider = mock(ObjectProvider.class);
    permissionService = mock(io.yak.framework.security.service.RbacPermissionService.class);
    when(permissionService.hasPermission(anyString(), anyString())).thenReturn(true);
    when(rbacProvider.orderedStream()).thenAnswer(invocation -> Stream.of(permissionService));
    qualityProvider = mock(ObjectProvider.class);
    modelFactsProvider = mock(ObjectProvider.class);
    ttlFactsProvider = mock(ObjectProvider.class);
    service = new AssetDiscoverService(assetAppService, registry, viewRecordService,
        statusFlowService, lineageProvider, securityProvider, rbacProvider,
        qualityProvider, modelFactsProvider, ttlFactsProvider);
    when(viewRecordService.trend(anyLong(), anyInt()))
        .thenReturn(List.of(new AssetViewRecordService.DailyView("2026-09-19", 2)));
  }

  @Test
  void missingExtensionsDegradePerSectionWithoutFailingDetail() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    when(lineageProvider.getIfAvailable()).thenReturn(null);
    when(securityProvider.getIfAvailable()).thenReturn(null);

    AssetDiscoverService.AssetDetailView detail = service.detail(1L, "alice");

    assertEquals(1L, detail.asset().id());
    assertEquals("OK", section(detail, "statusFlow").status());
    assertEquals("OK", section(detail, "trend").status());
    assertEquals("UNAVAILABLE", section(detail, "sourceAttrs").status());
    assertEquals("血缘服务未装配", section(detail, "lineage").note());
    assertEquals("安全域未装配", section(detail, "security").note());
    assertEquals("UNAVAILABLE", section(detail, "fields").status());
    assertEquals("UNAVAILABLE", section(detail, "quality").status());
    assertEquals("UNAVAILABLE", section(detail, "ttl").status());
  }

  @Test
  void sourceAttrsFlagChangedWhenFingerprintDrifts() {
    AssetItemPO po = modelItem();
    when(assetAppService.requireItem(1L)).thenReturn(po);
    AssetProvider provider = mock(AssetProvider.class);
    when(provider.refresh("42")).thenReturn(Optional.of(descriptor("other-hash")));
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.of(provider));
    when(lineageProvider.getIfAvailable()).thenReturn(null);
    when(securityProvider.getIfAvailable()).thenReturn(null);

    AssetDiscoverService.SectionView sourceAttrs = section(service.detail(1L, "alice"), "sourceAttrs");

    assertEquals("OK", sourceAttrs.status());
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
    when(lineageProvider.getIfAvailable()).thenReturn(null);
    when(securityProvider.getIfAvailable()).thenReturn(null);

    AssetDiscoverService.SectionView sourceAttrs = section(service.detail(1L, "alice"), "sourceAttrs");
    assertEquals("UNAVAILABLE", sourceAttrs.status());
    assertTrue(sourceAttrs.note().contains("db down"));
  }

  @Test
  void lineageResolvesAssetKeyThenReadsOneHopGraph() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    when(securityProvider.getIfAvailable()).thenReturn(null);
    LineageQueryService lineage = mock(LineageQueryService.class);
    LineageAsset root = mock(LineageAsset.class);
    when(root.id()).thenReturn(7L);
    when(lineage.getAssetByKey("modeling:model:42")).thenReturn(root);
    when(lineage.graph(7L, LineageDirection.BOTH, 1)).thenReturn(null);
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    assertEquals("OK", section(service.detail(1L, "alice"), "lineage").status());
  }

  @Test
  void lineageKeyWithoutRegistrationIsUnavailableNotError() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    when(securityProvider.getIfAvailable()).thenReturn(null);
    LineageQueryService lineage = mock(LineageQueryService.class);
    when(lineage.getAssetByKey(anyString())).thenThrow(new IllegalArgumentException("missing"));
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);

    assertEquals("UNAVAILABLE", section(service.detail(1L, "alice"), "lineage").status());
  }

  @Test
  void securityUsesLiveClassificationWhenKeyMatches() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());
    when(lineageProvider.getIfAvailable()).thenReturn(null);
    SecurityClassificationQueryApi api = mock(SecurityClassificationQueryApi.class);
    when(api.find("modeling:model:42")).thenReturn(null);
    when(securityProvider.getIfAvailable()).thenReturn(api);
    assertEquals("UNAVAILABLE", section(service.detail(1L, "alice"), "security").status());

    ClassificationView view = new ClassificationView("modeling:model:42", 3L, "L3", "内部",
        3, 9L, "PII", "个人信息");
    when(api.find("modeling:model:42")).thenReturn(view);
    AssetDiscoverService.SectionView security = section(service.detail(1L, "alice"), "security");
    assertEquals("OK", security.status());
    assertEquals(view, security.data());
  }

  @Test
  void manualAssetHasNoSourceDomain() {
    AssetItemPO po = modelItem();
    po.setSourceType(AssetSourceType.MANUAL.name());
    when(assetAppService.requireItem(1L)).thenReturn(po);
    when(lineageProvider.getIfAvailable()).thenReturn(null);
    when(securityProvider.getIfAvailable()).thenReturn(null);

    AssetDiscoverService.SectionView sourceAttrs = section(service.detail(1L, "alice"), "sourceAttrs");
    assertEquals("UNAVAILABLE", sourceAttrs.status());
    assertTrue(sourceAttrs.note().contains("手工登记"));
    assertNull(sourceAttrs.data());
  }

  @Test
  void singleSectionQueryDoesNotFanOutToOtherDomains() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(registry.find(AssetSourceType.MODEL)).thenReturn(Optional.empty());

    AssetDiscoverService.SectionView result = service.section(1L, "TECHNICAL_METADATA", "alice");

    assertEquals("UNAVAILABLE", result.status());
    verify(lineageProvider, never()).getIfAvailable();
    verify(securityProvider, never()).getIfAvailable();
  }

  @Test
  void deniedSecuritySectionReturnsNoSummary() {
    when(assetAppService.requireItem(1L)).thenReturn(modelItem());
    when(permissionService.hasPermission("alice", "data-security:read")).thenReturn(false);

    AssetDiscoverService.SectionView result = service.section(1L, "SECURITY", "alice");

    assertEquals("PERMISSION_DENIED", result.status());
    assertNull(result.data());
    verify(securityProvider, never()).getIfAvailable();
  }

  @Test
  void unsupportedQualitySectionIsExplicitlyNotApplicable() {
    AssetItemPO metric = modelItem();
    metric.setAssetType(AssetType.METRIC.name());
    when(assetAppService.requireItem(1L)).thenReturn(metric);

    AssetDiscoverService.SectionView result = service.section(1L, "QUALITY", "alice");

    assertEquals("NOT_APPLICABLE", result.status());
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
