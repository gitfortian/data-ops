package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataServiceContractPayload;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import io.yak.ops.business.consumption.product.provider.source.DataServiceDataProductProvider;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path B, slice 1: discovery and canonical Data Service identity. */
class DataServiceGoldenDiscoveryTest {

  @Test
  void discoverySourceShortcutAndCanonicalDetailResolveTheSamePublishedService() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 14, 0);
    DataServiceDefinition definition = DataServiceDefinition.restore(
        88L,
        7L,
        4L,
        new DataServiceSettings(
            "Orders API", "/orders", 500, 30, true, "Published orders service", true),
        new PublishedRuntimeSnapshot(3L, "select * from orders where tenant_id=:tenantId"),
        new SourceReference("DEVELOPMENT_TASK", "task-55", 9001L, 12),
        RuntimePolicy.defaults(true),
        AuthMode.API_KEY,
        observedAt,
        observedAt);
    DataServiceView view = new DataServiceView(
        88L,
        "Orders API",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        3L,
        "select * from orders where tenant_id=:tenantId",
        List.of("tenantId"),
        500,
        30,
        true,
        "API_KEY",
        "Published orders service",
        "DEVELOPMENT_TASK",
        "task-55",
        9001L,
        12,
        observedAt,
        observedAt,
        true);

    when(reader.list()).thenReturn(List.of(definition));
    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view);
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(
        new AssetSourceLookupService.SourceLookup(
            "NOT_INDEXED", "DATA_SERVICE", "88", null, null, null, null));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);
    ProductDiscoveryService discoveryService =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonicalService =
        new CanonicalProductService(discoveryService, assetAppService);

    ProductDiscoveryResult discovery = discoveryService.search(new ProductSearchCriteria(
        ProductType.DATA_SERVICE,
        "orders",
        null,
        7L,
        null,
        SourceLifecycleState.PUBLISHED,
        null));

    assertEquals(ProductSearchState.READY, discovery.providerStates().get(ProductType.DATA_SERVICE));
    assertEquals(1L, discovery.total());
    assertEquals(1, discovery.products().size());

    ProductKey discoveredKey = discovery.products().get(0).productKey();
    assertEquals("DATA_SERVICE:88", discoveredKey.value());

    CanonicalProductService.NavigationResolution shortcut =
        canonicalService.fromSource(ProductType.DATA_SERVICE, "88");
    assertEquals("FOUND", shortcut.state());
    assertEquals(discoveredKey, shortcut.productKey());
    assertEquals("/data-analysis/consumption/DATA_SERVICE%3A88", shortcut.canonicalHref());

    CanonicalProductDetail detail = canonicalService.detail(discoveredKey);
    assertEquals(ProductLookupState.FOUND, detail.state());
    assertNotNull(detail.product());
    assertEquals(discoveredKey, detail.product().productKey());
    assertEquals("88", detail.product().sourceRef().sourceIdentity());
    assertEquals("9001", detail.product().activeVersion().identity());
    assertEquals("v12", detail.product().activeVersion().displayVersion());
    assertEquals("/data-service/api/88", detail.navigation().sourceHref());

    DataServiceContractPayload contract = assertInstanceOf(
        DataServiceContractPayload.class, detail.product().contractPayload());
    assertEquals("/api/v1/data-service/runtime/orders", contract.runtimePath());
    assertEquals(List.of("tenantId"), contract.parameterNames());
    assertEquals("API_KEY", contract.authMode());
    assertEquals(9001L, contract.sourceRevisionId());
    assertEquals(12, contract.sourceRevisionNo());
  }
}
