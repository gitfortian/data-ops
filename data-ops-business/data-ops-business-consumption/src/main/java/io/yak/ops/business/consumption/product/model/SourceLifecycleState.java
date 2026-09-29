package io.yak.ops.business.consumption.product.model;

/** Publication/lifecycle truth supplied by the owning source domain. */
public enum SourceLifecycleState {
  NOT_PUBLISHED,
  PUBLISHED,
  DEPRECATED,
  RETIRED
}
