package io.yak.ops.business.dashboard.gateway.analysis;

import io.yak.ops.business.analysis.AnalysisReferenceService;
import org.springframework.stereotype.Component;

/** Adapts Analysis' narrow reference facade to the Dashboard-owned gateway. */
@Component
public class AnalysisDashboardAdapter implements DashboardAnalysisGateway {

  private final AnalysisReferenceService analyses;

  public AnalysisDashboardAdapter(AnalysisReferenceService analyses) {
    this.analyses = analyses;
  }

  @Override
  public void requireExists(long analysisId) {
    analyses.requireExists(analysisId);
  }
}
