package io.yak.ops.business.consumption.product.model;

import java.time.Instant;

/** Read-side state/provenance for one optional governance or usage section. */
public record ProductSectionState(
    String sectionKey,
    ProviderEvidenceState state,
    String ownerDomain,
    Instant observedAt,
    String reason) {

  public ProductSectionState {
    if (sectionKey == null || sectionKey.isBlank()) {
      throw new IllegalArgumentException("sectionKey must not be blank");
    }
    if (state == null) {
      throw new NullPointerException("state");
    }
    if (ownerDomain == null || ownerDomain.isBlank()) {
      throw new IllegalArgumentException("ownerDomain must not be blank");
    }
    sectionKey = sectionKey.trim();
    ownerDomain = ownerDomain.trim();
  }
}
