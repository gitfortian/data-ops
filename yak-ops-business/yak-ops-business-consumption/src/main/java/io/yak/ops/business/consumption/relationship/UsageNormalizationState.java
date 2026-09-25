package io.yak.ops.business.consumption.relationship;

/** Outcome of converting source-owned audit evidence into normalized Consumption usage. */
public enum UsageNormalizationState {
  NORMALIZED,
  IGNORED,
  GAP,
  UNAVAILABLE
}
