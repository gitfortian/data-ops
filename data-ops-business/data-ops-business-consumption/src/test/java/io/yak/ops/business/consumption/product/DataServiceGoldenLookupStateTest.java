package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.source.DataServiceDataProductProvider;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path B: missing and non-discoverable services are distinct states. */
class DataServiceGoldenLookupStateTest {

  @Test
  void missingServiceAndLegacyUnscopedServiceRemainDistinctCanonicalStates() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    when(reader.require(404L)).thenThrow(new IllegalArgumentException("Data Service not found"));
    when(reader.require(88L)).thenReturn(legacyUnscopedDefinition());

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);
    CanonicalProductService canonical = new CanonicalProductService(
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider))), assetAppService);

    CanonicalProductDetail missing =
        canonical.detail(new ProductKey(ProductType.DATA_SERVICE, "404"));
    CanonicalProductDetail notDiscoverable =
        canonical.detail(new ProductKey(ProductType.DATA_SERVICE, "88"));

    assertEquals(ProductLookupState.NOT_FOUND, missing.state());
    assertNull(missing.product());
    assertNull(missing.navigation());

    assertEquals(ProductLookupState.NOT_DISCOVERABLE, notDiscoverable.state());
    assertNull(notDiscoverable.product());
    assertNull(notDiscoverable.navigation());

    verifyNoInteractions(viewFactory, assetLookup, assetAppService);
  }

  private DataServiceDefinition legacyUnscopedDefinition() {
    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 14, 0);
    return DataServiceDefinition.restore(
        88L,
        null,
        1L,
        new DataServiceSettings(
            "Legacy Orders API", "/legacy-orders", 100, 30, true, null, false),
        new PublishedRuntimeSnapshot(3L, "select * from orders"),
        new SourceReference("DEVELOPMENT_TASK", "task-55", 9001L, 12),
        RuntimePolicy.defaults(true),
        AuthMode.API_KEY,
        observedAt,
        observedAt);
  }
}
