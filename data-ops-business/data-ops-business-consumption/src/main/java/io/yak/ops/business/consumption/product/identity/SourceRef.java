package io.yak.ops.business.consumption.product.identity;

import io.yak.ops.business.consumption.product.model.ProductType;
import java.util.Objects;

/** Owning source identity carried by the consumption projection. */
public record SourceRef(ProductType productType, String sourceIdentity) {

  public SourceRef {
    Objects.requireNonNull(productType, "productType");
    if (sourceIdentity == null || sourceIdentity.isBlank()) {
      throw new IllegalArgumentException("sourceIdentity must not be blank");
    }
    sourceIdentity = sourceIdentity.trim();
  }

  public ProductKey productKey() {
    return new ProductKey(productType, sourceIdentity);
  }
}
