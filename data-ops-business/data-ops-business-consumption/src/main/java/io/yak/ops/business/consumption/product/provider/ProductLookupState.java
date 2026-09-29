package io.yak.ops.business.consumption.product.provider;

/** Internal lookup outcome. API layers may intentionally mask NOT_DISCOVERABLE to avoid existence leakage. */
public enum ProductLookupState {
  FOUND,
  NOT_FOUND,
  NOT_DISCOVERABLE,
  FORBIDDEN,
  UNAVAILABLE
}
