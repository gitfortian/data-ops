package io.yak.ops.business.lineage.domain;

/**
 * Typed metadata relationships. Every edge is persisted from upstream asset to downstream asset.
 * The type describes how the downstream target uses, derives from or contains the upstream source.
 */
public enum LineageRelationType {
  READS_FROM,
  WRITES_TO,
  DERIVES_FROM,
  CONSUMES,
  CONTAINS
}
