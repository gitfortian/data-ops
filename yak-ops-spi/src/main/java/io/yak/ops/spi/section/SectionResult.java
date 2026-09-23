package io.yak.ops.spi.section;

/**
 * Stable response model exposed by Section Query API.
 */
public record SectionResult(
        String sectionId,
        SectionStatus status,
        Object payload,
        String message) {
}
