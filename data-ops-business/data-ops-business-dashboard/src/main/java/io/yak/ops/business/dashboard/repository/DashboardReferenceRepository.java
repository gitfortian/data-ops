package io.yak.ops.business.dashboard.repository;

/** Read-only persistence port for cross-domain Dashboard references. */
public interface DashboardReferenceRepository {

  boolean existsAnalysisReference(long analysisId);
}
