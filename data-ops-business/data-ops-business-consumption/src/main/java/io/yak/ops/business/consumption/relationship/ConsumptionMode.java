package io.yak.ops.business.consumption.relationship;

/** Declared or observed way a consumer depends on a governed product. */
public enum ConsumptionMode {
  QUERY,
  PREVIEW,
  EXPORT,
  API_INVOKE,
  DOWNSTREAM
}
