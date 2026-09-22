package io.yak.ops.business.analysis;

/** Cross-domain extension point for assets that may block Analysis deletion. */
@FunctionalInterface
public interface AnalysisDeletionGuard {

  void requireDeletable(long analysisId);
}
