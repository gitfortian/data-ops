package io.yak.ops.business.consumption.product.model;

/** Uniform state for optional/read-side evidence sections and providers. */
public enum ProviderEvidenceState {
  READY,
  EMPTY,
  UNAVAILABLE,
  FORBIDDEN,
  NOT_APPLICABLE
}
