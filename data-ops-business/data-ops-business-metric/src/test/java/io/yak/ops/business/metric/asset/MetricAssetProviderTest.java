package io.yak.ops.business.metric.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.metric.dao.mapper.MetricMapper;
import io.yak.ops.business.metric.lineage.MetricLineageRegistrationService;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.metric.dao.model.MetricPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * METRIC provider 契约单测:asset_key 与血缘登记键逐字符一致(D6);
 * 域编码解析失败只影响展示列、不改指纹(避免 semantic 抖动误报变更)。
 */
class MetricAssetProviderTest {

  private MetricMapper mapper;
  private ObjectProvider<ProcessApi> processApi;
  private MetricAssetProvider provider;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    mapper = Mockito.mock(MetricMapper.class);
    processApi = mock(ObjectProvider.class);
    provider = new MetricAssetProvider(mapper, processApi);
  }

  @Test
  void assetKeyMatchesLineageRegistrationKeyExactly() {
    when(processApi.getIfAvailable()).thenReturn(null);
    when(mapper.selectList(any())).thenReturn(List.of(metric(9L, 3L)));
    AssetPage page = provider.cursorList(new AssetCursorQuery(1L, null, null, 10));
    AssetDescriptor item = page.items().get(0);
    assertEquals("metric:9", item.assetKey());
    assertEquals(MetricLineageRegistrationService.metricAssetKey(9L), item.assetKey());
    assertEquals("9", item.sourceId());
  }

  @Test
  void descriptorMapsMetricAndResolvesDomainCode() {
    ProcessApi api = mock(ProcessApi.class);
    when(api.listDomains())
        .thenReturn(List.of(domain(3L, "trade", "交易域")));
    when(processApi.getIfAvailable()).thenReturn(api);
    when(mapper.selectList(any())).thenReturn(List.of(metric(9L, 3L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("支付金额", item.name());
    assertEquals(AssetType.METRIC, item.assetType());
    assertEquals(AssetSourceType.METRIC, provider.sourceType());
    assertEquals("trade", item.domainCode());
    assertNull(item.layerCode());
    assertEquals("bob", item.suggestedOwner());
    assertEquals("pay_amt", item.extra().get("metricCode"));
    assertTrue(item.contentHash().length() == 64);
  }

  @Test
  void domainResolutionFailureKeepsContentHashStable() {
    MetricPO po = metric(9L, 3L);
    MetricAssetProvider bare = new MetricAssetProvider(mapper, emptyProvider());
    String withDomain = provider.toDescriptor(po, Map.of(3L, "trade")).contentHash();
    String withoutDomain = bare.toDescriptor(po, Map.of()).contentHash();
    assertEquals(withDomain, withoutDomain);
    assertEquals("trade", provider.toDescriptor(po, Map.of(3L, "trade")).domainCode());
    assertNull(bare.toDescriptor(po, Map.of()).domainCode());
  }

  @Test
  void refreshHandlesMissingAndBadIds() {
    when(processApi.getIfAvailable()).thenReturn(null);
    when(mapper.selectById(11L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("11"));
    assertEquals(Optional.empty(), provider.refresh("x"));
    when(mapper.selectById(12L)).thenReturn(metric(12L, 3L));
    assertEquals("metric:12", provider.refresh("12").orElseThrow().assetKey());
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<ProcessApi> emptyProvider() {
    ObjectProvider<ProcessApi> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  private static MetricPO metric(Long id, Long domainId) {
    MetricPO po = new MetricPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setMetricCode("pay_amt");
    po.setMetricName("支付金额");
    po.setDomainId(domainId);
    po.setMetricType("ATOMIC");
    po.setStatus("ENABLED");
    po.setOwner("bob");
    po.setVersion(1);
    po.setUpdateTime(LocalDateTime.now());
    return po;
  }

  private static BusinessDomain domain(Long id, String code, String name) {
    return new BusinessDomain(id, code, name, BusinessDomain.ROOT_PARENT_ID, null, null,
        10, null, null, null);
  }
}
