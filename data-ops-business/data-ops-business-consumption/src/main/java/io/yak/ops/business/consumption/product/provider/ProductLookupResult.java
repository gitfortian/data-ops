package io.yak.ops.business.consumption.product.provider;

import io.yak.ops.business.consumption.product.model.DataProductView;

public record ProductLookupResult(
    ProductLookupState state,
    DataProductView product,
    String reason) {

  public ProductLookupResult {
    if (state == null) {
      throw new NullPointerException("state");
    }
    if (state == ProductLookupState.FOUND && product == null) {
      throw new IllegalArgumentException("FOUND lookup requires product");
    }
    if (state != ProductLookupState.FOUND && product != null) {
      throw new IllegalArgumentException("non-FOUND lookup must not carry product");
    }
  }

  public static ProductLookupResult found(DataProductView product) {
    return new ProductLookupResult(ProductLookupState.FOUND, product, null);
  }

  public static ProductLookupResult notFound() {
    return new ProductLookupResult(ProductLookupState.NOT_FOUND, null, null);
  }

  public static ProductLookupResult notDiscoverable() {
    return new ProductLookupResult(ProductLookupState.NOT_DISCOVERABLE, null, null);
  }

  public static ProductLookupResult forbidden(String reason) {
    return new ProductLookupResult(ProductLookupState.FORBIDDEN, null, reason);
  }

  public static ProductLookupResult unavailable(String reason) {
    return new ProductLookupResult(ProductLookupState.UNAVAILABLE, null, reason);
  }
}
