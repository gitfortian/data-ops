package io.yak.ops.spi.section;

/** Stable Asset identity and source identity resolved by the Asset boundary. */
public record SectionContext(String assetKey, String sourceType, String sourceId) {
}
