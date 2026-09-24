package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.consumption.product.identity.DomainRef;

/** Stable navigation targets around one canonical Consumption Detail. */
public record ConsumptionNavigation(
    String canonicalHref,
    String sourceHref,
    DomainLink asset,
    DomainLink producer) {

  public record DomainLink(DomainRef ref, String href) {}
}
