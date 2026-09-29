package io.yak.ops.spi.section;

/**
 * Availability of the owning domain's read-side for this asset type.
 * A missing integration is UNAVAILABLE, not proof that the fact is absent.
 */
public record SectionCapability(boolean applicable, boolean available, String reason) {
}
