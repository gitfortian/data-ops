package io.yak.ops.business.consumption.product.model;

/** Runtime/provider evidence about whether the active source contract can be consumed now. */
public enum AvailabilityState {
  AVAILABLE,
  UNAVAILABLE,
  UNKNOWN
}
