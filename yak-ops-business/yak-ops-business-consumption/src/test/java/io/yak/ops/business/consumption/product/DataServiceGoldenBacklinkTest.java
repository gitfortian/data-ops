package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
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

/** Phase 4 #104 Golden Path B: stable Data Service source, Asset and producer backlinks. */
class DataServiceGoldenBacklinkTest {

  @Test
  void canonicalDetailAndAssetShortcutKeepStableServiceAssetAndProducerIdentities() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 15, 20);
    DataServiceDefinition definition = DataServiceDefinition.restore(
        88L,
        7L,
        4L,
        new DataServiceSettings(
            "Orders API", "/orders", 500, 30, true, "Published orders service", true),
        new PublishedRuntimeSnapshot(3L, "select * from orders where tenant_id=:tenantId"),
        new SourceReference("DATA_DEVELOPMENT_DATA_SERVICE", "55", 9001L, 12),
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
        "DATA_DEVELOPMENT_DATA_SERVICE",
        "55",
        9001L,
        12,
        observedAt,
        observedAt,
        true);

    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view);
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(
        new AssetSourceLookupService.SourceLookup(
            "FOUND", "DATA_SERVICE", "88", 701L, "data-service:88", "API", "ONLINE"));
    when(assetAppService.get(701L)).thenReturn(new AssetAppService.AssetView(
        701L,
        "data-service:88",
        "DATA_SERVICE",
        "88",
        "API",
        "Orders API",
        "Published orders service",
        null,
        null,
        null,
        "owner",
        "ONLINE",
        null,
        null,
        null,
        0,
        "/data-service/api/88",
        observedAt,
        observedAt,
        observedAt,
        null,
        null,
        observedAt,
        observedAt,
        observedAt));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);
    CanonicalProductService canonicalService = new CanonicalProductService(
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider))),
        assetAppService);

    ProductKey key = new ProductKey(ProductType.DATA_SERVICE, "88");
    CanonicalProductDetail detail = canonicalService.detail(key);

    assertNotNull(detail.product());
    assertEquals(key, detail.product().productKey());
    assertEquals("/data-analysis/consumption/DATA_SERVICE%3A88", detail.navigation().canonicalHref());
    assertEquals("/data-service/api/88", detail.navigation().sourceHref());
    assertEquals("/data-asset/detail/701", detail.navigation().assetHref());
    assertEquals("/data-development?nodeId=55", detail.navigation().producerHref());

    CanonicalProductService.NavigationResolution assetShortcut = canonicalService.fromAsset(701L);
    assertEquals("FOUND", assetShortcut.state());
    assertEquals(key, assetShortcut.productKey());
    assertEquals("/data-analysis/consumption/DATA_SERVICE%3A88", assetShortcut.canonicalHref());
  }
}
