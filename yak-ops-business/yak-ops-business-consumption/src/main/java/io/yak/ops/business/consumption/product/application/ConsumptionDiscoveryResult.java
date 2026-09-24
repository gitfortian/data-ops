package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import java.util.Comparator;
import java.util.List;

/** Cross-provider discovery result. Partial failure stays explicit instead of becoming fake empty. */
public record ConsumptionDiscoveryResult(
    List<DataProductView> products,
    long total,
    boolean partial,
    List<ProviderObservation> providers) {

  public ConsumptionDiscoveryResult {
    products = products == null ? List.of() : products.stream()
        .sorted(Comparator.comparing(product -> product.productKey().value()))
        .toList();
    providers = providers == null ? List.of() : List.copyOf(providers);
    if (total < products.size()) throw new IllegalArgumentException("total must cover returned products");
  }

  public boolean confirmedEmpty() {
    return !partial && products.isEmpty();
  }

  public record ProviderObservation(
      ProductType productType,
      ProductSearchState state,
      long total,
      String reason) {}
}
