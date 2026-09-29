package io.yak.ops.business.consumption.product.model;

/** Authorization decision for a concrete subject/action/plane. Provider availability is separate. */
public enum AccessDecision {
  ALLOWED,
  REQUEST_REQUIRED,
  FORBIDDEN,
  NOT_APPLICABLE
}
