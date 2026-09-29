package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductDiscoveryServiceTest {

  @Test
  void aggregateSearchPreservesPartialProviderFailureInsteadOfFabricatingEmpty() {
    DataProductProvider dataset = provider(
        ProductType.DATASET, ProductSearchResult.ready(List.of(), 0), ProductLookupResult.notFound());
    DataProductProvider service = provider(
        ProductType.DATA_SERVICE,
        ProductSearchResult.unavailable("data-service source unavailable"),
        ProductLookupResult.unavailable("data-service source unavailable"));
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(dataset, service)));

    ProductDiscoveryResult result = discovery.search(emptyCriteria());

    assertEquals(ProductSearchState.READY, result.providerStates().get(ProductType.DATASET));
    assertEquals(ProductSearchState.UNAVAILABLE, result.providerStates().get(ProductType.DATA_SERVICE));
    assertEquals("data-service source unavailable", result.providerReasons().get(ProductType.DATA_SERVICE));
    assertEquals(List.of(), result.products());
    assertEquals(0L, result.total());
  }

  @Test
  void missingTypedProviderIsUnavailableAndExactLookupDoesNotBecomeNotFound() {
    ProductDiscoveryService discovery = new ProductDiscoveryService(new DataProductRegistry(List.of()));
    ProductSearchCriteria typed = new ProductSearchCriteria(
        ProductType.DATASET, null, null, null, null, null, null);

    ProductDiscoveryResult search = discovery.search(typed);
    ProductLookupResult lookup = discovery.get(new ProductKey(ProductType.DATASET, "42"));

    assertEquals(ProductSearchState.UNAVAILABLE, search.providerStates().get(ProductType.DATASET));
    assertEquals(ProductLookupState.UNAVAILABLE, lookup.state());
  }

  private static ProductSearchCriteria emptyCriteria() {
    return new ProductSearchCriteria(null, null, null, null, null, null, null);
  }

  private static DataProductProvider provider(
      ProductType type, ProductSearchResult search, ProductLookupResult lookup) {
    return new DataProductProvider() {
      @Override public ProductType productType() { return type; }
      @Override public ProductLookupResult get(ProductKey productKey) { return lookup; }
      @Override public ProductSearchResult search(ProductSearchCriteria criteria) { return search; }
    };
  }
}
