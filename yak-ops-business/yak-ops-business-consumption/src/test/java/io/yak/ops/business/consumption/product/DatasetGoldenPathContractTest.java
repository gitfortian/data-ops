package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase 4 Golden Path A skeleton.
 *
 * <p>Locks the first acceptance segment to one source-derived Dataset product identity across
 * discovery and canonical detail. Query authorization, execution and usage evidence are added in
 * later #104 slices instead of being mocked into this contract.
 */
class DatasetGoldenPathContractTest {

  @Test
  void datasetSourceIdentityStaysStableFromDiscoveryToCanonicalDetail() {
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DataProductView product = mock(DataProductView.class);
    when(product.productKey()).thenReturn(key);
    when(product.sections()).thenReturn(List.of());

    DataProductProvider datasetProvider = new DataProductProvider() {
      @Override
      public ProductType productType() {
        return ProductType.DATASET;
      }

      @Override
      public ProductLookupResult get(ProductKey productKey) {
        return key.equals(productKey)
            ? ProductLookupResult.found(product)
            : ProductLookupResult.notFound();
      }

      @Override
      public ProductSearchResult search(ProductSearchCriteria criteria) {
        return ProductSearchResult.ready(List.of(product), 1);
      }
    };

    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(datasetProvider)));
    CanonicalProductService canonical =
        new CanonicalProductService(discovery, mock(AssetAppService.class));

    ProductDiscoveryResult discovered = discovery.search(new ProductSearchCriteria(
        ProductType.DATASET, null, null, null, null, null, null));
    CanonicalProductDetail detail = canonical.detail(key);

    assertEquals(1, discovered.products().size());
    assertEquals(key, discovered.products().get(0).productKey());
    assertEquals(ProductLookupState.FOUND, detail.state());
    assertEquals(key, detail.product().productKey());
    assertEquals("/data-analysis/consumption/DATASET%3A42", detail.navigation().canonicalHref());
    assertEquals("/dataset/42", detail.navigation().sourceHref());
  }
}
