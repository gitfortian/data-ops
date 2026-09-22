package io.yak.ops.business.dashboard.gateway.analysis;

/** Dashboard-owned boundary for validating reusable Analysis references. */
public interface DashboardAnalysisGateway {

  void requireExists(long analysisId);
}
