package io.yak.ops.spi.section;

import java.util.Map;

/** Stable Asset identity and source identity resolved by the Asset boundary. */
public record SectionContext(
    String assetKey, String sourceType, String sourceId, Map<String, String> attributes) {
    public SectionContext(String assetKey, String sourceType, String sourceId) {
        this(assetKey, sourceType, sourceId, Map.of());
    }

    public SectionContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
