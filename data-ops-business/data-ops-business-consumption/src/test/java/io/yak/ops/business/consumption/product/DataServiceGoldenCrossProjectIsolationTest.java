package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import io.yak.ops.business.consumption.product.provider.source.DataServiceDataProductProvider;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path B: cross-project Data Service requests stay outside Consumption. */
class DataServiceGoldenCrossProjectIsolationTest {

  @Test
  void foreignProjectDataServiceIsNeitherDiscoverableNorResolvableByStableProductKey() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    // DataServiceReader's management-plane list/require operations are scoped by the trusted
    // CurrentProject in DataServiceRepositoryAdapter. A service owned by another Project Space is
    // therefore absent from both catalog discovery and direct stable-ID lookup.
    when(reader.list()).thenReturn(List.of());
    when(reader.require(88L)).thenThrow(new IllegalArgumentException("数据服务不存在：88"));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);
    ProductDiscoveryService discoveryService =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonicalService =
        new CanonicalProductService(discoveryService, assetAppService);

    ProductDiscoveryResult discovery = discoveryService.search(new ProductSearchCriteria(
        ProductType.DATA_SERVICE,
        null,
        null,
        null,
        null,
        null,
        null));

    assertEquals(ProductSearchState.READY, discovery.providerStates().get(ProductType.DATA_SERVICE));
    assertEquals(0L, discovery.total());
    assertEquals(List.of(), discovery.products());

    ProductKey foreignKey = new ProductKey(ProductType.DATA_SERVICE, "88");
    CanonicalProductDetail detail = canonicalService.detail(foreignKey);

    assertEquals(ProductLookupState.NOT_FOUND, detail.state());
    assertNull(detail.product());
    assertNull(detail.navigation());
    assertEquals(List.of(), detail.governanceEvidence());

    verify(reader).list();
    verify(reader).require(88L);
  }
}
