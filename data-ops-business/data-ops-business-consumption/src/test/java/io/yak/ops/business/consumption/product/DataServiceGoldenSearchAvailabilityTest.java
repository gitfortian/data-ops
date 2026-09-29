package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import io.yak.ops.business.consumption.product.provider.source.DataServiceDataProductProvider;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Phase 4 #104 Golden Path B: empty discovery is distinct from source-provider outage. */
class DataServiceGoldenSearchAvailabilityTest {

  @Test
  void emptyCatalogIsReadyButReaderFailureIsUnavailable() {
    DataServiceReader reader = Mockito.mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = Mockito.mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = Mockito.mock(AssetSourceLookupService.class);

    Mockito.when(reader.list())
        .thenReturn(List.of())
        .thenThrow(new IllegalStateException("data-service catalog unavailable"));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    ProductSearchCriteria criteria = new ProductSearchCriteria(
        ProductType.DATA_SERVICE,
        null,
        null,
        null,
        null,
        null,
        null);

    ProductDiscoveryResult empty = discovery.search(criteria);

    assertEquals(ProductSearchState.READY, empty.providerStates().get(ProductType.DATA_SERVICE));
    assertEquals(0L, empty.total());
    assertEquals(List.of(), empty.products());
    assertEquals(null, empty.providerReasons().get(ProductType.DATA_SERVICE));

    ProductDiscoveryResult unavailable = discovery.search(criteria);

    assertEquals(
        ProductSearchState.UNAVAILABLE,
        unavailable.providerStates().get(ProductType.DATA_SERVICE));
    assertEquals(0L, unavailable.total());
    assertEquals(List.of(), unavailable.products());
    assertEquals(
        "data-service catalog unavailable",
        unavailable.providerReasons().get(ProductType.DATA_SERVICE));
  }
}
