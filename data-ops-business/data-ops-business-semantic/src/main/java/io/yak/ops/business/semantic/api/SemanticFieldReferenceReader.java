package io.yak.ops.business.semantic.api;

/**
 * Consumer-owned, read-only reverse reference check for the standard field library.
 * Semantic declares the SPI, but never imports Modeling internals or reads its tables.
 */
public interface SemanticFieldReferenceReader {

  /**
   * Count persisted references to a standard field in the trusted current Project Space.
   * Includes references in recoverable/recycled consumer objects until actually detached.
   * Failure to read must propagate to the caller rather than be treated as zero references.
   */
  long countFieldReferences(Long fieldId);
}
