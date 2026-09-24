package io.yak.ops.business.consumption.product.identity;

import io.yak.ops.business.consumption.product.model.ProductType;
import java.util.Locale;
import java.util.Objects;

/** Stable governed-projection identity derived only from the owning source identity. */
public record ProductKey(ProductType productType, String sourceIdentity) {

  public ProductKey {
    Objects.requireNonNull(productType, "productType");
    sourceIdentity = requireText(sourceIdentity, "sourceIdentity");
  }

  public String value() {
    return productType.name().toUpperCase(Locale.ROOT) + ":" + sourceIdentity;
  }

  public static ProductKey parse(String value) {
    String text = requireText(value, "value");
    int separator = text.indexOf(':');
    if (separator <= 0 || separator == text.length() - 1) {
      throw new IllegalArgumentException("Invalid ProductKey: " + value);
    }
    ProductType type = ProductType.valueOf(text.substring(0, separator).trim().toUpperCase(Locale.ROOT));
    return new ProductKey(type, text.substring(separator + 1));
  }

  @Override
  public String toString() {
    return value();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
