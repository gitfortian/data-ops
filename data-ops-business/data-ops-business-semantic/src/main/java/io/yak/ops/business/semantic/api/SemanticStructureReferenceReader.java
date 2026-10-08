package io.yak.ops.business.semantic.api;

/**
 * Read-only reverse-reference contract for Semantic business domains and
 * processes. Consumer domains own the persisted foreign reference facts.
 * A failed query must propagate: it is never evidence of zero references.
 */
public interface SemanticStructureReferenceReader {

  /** Number of persisted references to the process in the trusted Project Space. */
  long countProcessReferences(Long processId);

  /** Number of persisted references to the domain in the trusted Project Space. */
  long countDomainReferences(Long domainId);
}
