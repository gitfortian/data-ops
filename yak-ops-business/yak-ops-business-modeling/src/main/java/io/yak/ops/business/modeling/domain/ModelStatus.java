package io.yak.ops.business.modeling.domain;

/**
 * Lifecycle status of a physical model.
 *
 * <p>DRAFT → (publish) → PUBLISHED → (edit/save) → DRAFT → ...
 * DISABLED marks models excluded from derivation and lineage.
 */
public enum ModelStatus {
  DRAFT,
  PUBLISHED,
  DISABLED
}
