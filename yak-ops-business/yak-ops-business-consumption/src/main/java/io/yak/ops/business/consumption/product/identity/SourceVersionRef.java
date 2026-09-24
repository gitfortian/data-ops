package io.yak.ops.business.consumption.product.identity;

/** Source-owned active Dataset version or Data Service revision reference. */
public record SourceVersionRef(String identity, String displayVersion) {

  public SourceVersionRef {
    if (identity == null || identity.isBlank()) {
      throw new IllegalArgumentException("identity must not be blank");
    }
    identity = identity.trim();
    displayVersion = displayVersion == null || displayVersion.isBlank() ? null : displayVersion.trim();
  }
}
