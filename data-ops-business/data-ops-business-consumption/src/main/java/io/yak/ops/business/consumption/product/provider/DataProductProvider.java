package io.yak.ops.business.consumption.product.provider;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;

/** Source-domain SPI. Implementations project owning truth; they do not transfer ownership to Consumption. */
public interface DataProductProvider {

  ProductType productType();

  ProductLookupResult get(ProductKey productKey);

  ProductSearchResult search(ProductSearchCriteria criteria);
}
