package io.yak.ops.business.consumption.product.identity;

/** Stable reference back to another owning domain such as Asset or Development. */
public record DomainRef(String domain, String identity) {

  public DomainRef {
    if (domain == null || domain.isBlank()) {
      throw new IllegalArgumentException("domain must not be blank");
    }
    if (identity == null || identity.isBlank()) {
      throw new IllegalArgumentException("identity must not be blank");
    }
    domain = domain.trim();
    identity = identity.trim();
  }
}
