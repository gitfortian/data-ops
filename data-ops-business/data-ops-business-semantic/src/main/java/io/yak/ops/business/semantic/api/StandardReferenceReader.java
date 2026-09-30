package io.yak.ops.business.semantic.api;

/** Read-only reference count supplied by domains that consume semantic standards. */
public interface StandardReferenceReader {

  /** Counts persisted references in the current project for one standard or code set. */
  long countReferences(StandardKind kind, Long standardId, String codeSetCode);
}
