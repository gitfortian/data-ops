package io.yak.ops.business.consumption.model;

/**
 * Consumer-facing state. Source domains keep their own lifecycle truth.
 */
public enum ConsumptionState {
    AVAILABLE,
    UNAVAILABLE,
    FORBIDDEN,
    EMPTY
}
