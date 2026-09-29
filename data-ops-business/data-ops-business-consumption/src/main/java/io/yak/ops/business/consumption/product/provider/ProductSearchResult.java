package io.yak.ops.business.consumption.product.provider;

import io.yak.ops.business.consumption.product.model.DataProductView;
import java.util.List;

/** Search response keeps confirmed-empty distinct from provider failure. */
public record ProductSearchResult(
    ProductSearchState state,
    List<DataProductView> products,
    long total,
    String reason) {

  public ProductSearchResult {
    if (state == null) {
      throw new NullPointerException("state");
    }
    products = products == null ? List.of() : List.copyOf(products);
    if (state != ProductSearchState.READY && (!products.isEmpty() || total != 0)) {
      throw new IllegalArgumentException("non-READY search must not fabricate results");
    }
    if (total < products.size()) {
      throw new IllegalArgumentException("total must be >= returned product count");
    }
  }

  public static ProductSearchResult ready(List<DataProductView> products, long total) {
    return new ProductSearchResult(ProductSearchState.READY, products, total, null);
  }

  public static ProductSearchResult forbidden(String reason) {
    return new ProductSearchResult(ProductSearchState.FORBIDDEN, List.of(), 0, reason);
  }

  public static ProductSearchResult unavailable(String reason) {
    return new ProductSearchResult(ProductSearchState.UNAVAILABLE, List.of(), 0, reason);
  }
}
