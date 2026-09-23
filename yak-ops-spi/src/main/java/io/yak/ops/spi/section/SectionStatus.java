package io.yak.ops.spi.section;

/**
 * Lifecycle state shared by all Asset Detail sections.
 */
public enum SectionStatus {
    AVAILABLE,
    LOADING,
    EMPTY,
    DEGRADED,
    FAILED
}
