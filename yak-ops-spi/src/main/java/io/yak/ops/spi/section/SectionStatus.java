package io.yak.ops.spi.section;

/** A source domain must distinguish absence, inapplicability, outage and denial. */
public enum SectionStatus {
    OK,
    EMPTY,
    NOT_APPLICABLE,
    UNAVAILABLE,
    PERMISSION_DENIED
}
