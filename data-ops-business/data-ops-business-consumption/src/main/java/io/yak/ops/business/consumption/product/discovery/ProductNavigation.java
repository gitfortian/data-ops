package io.yak.ops.business.consumption.product.discovery;

/** Stable UI navigation derived from ProductKey and owning-domain references only. */
public record ProductNavigation(
    String canonicalHref,
    String sourceHref,
    String assetHref,
    String producerHref) {}
