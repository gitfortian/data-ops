package io.yak.ops.business.consumption.product.discovery;

import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import java.util.List;
import java.util.Map;

/**
 * Discovery response preserving provider evidence. A partial result never pretends that a failed
 * provider was an empty provider.
 */
public record ProductDiscoveryResult(
    List<DataProductView> products,
    long total,
    Map<ProductType, ProductSearchState> providerStates,
    Map<ProductType, String> providerReasons) {

  public ProductDiscoveryResult {
    products = products == null ? List.of() : List.copyOf(products);
    providerStates = providerStates == null ? Map.of() : Map.copyOf(providerStates);
    providerReasons = providerReasons == null ? Map.of() : Map.copyOf(providerReasons);
    if (total < products.size()) {
      throw new IllegalArgumentException("total must be >= returned product count");
    }
  }
}
