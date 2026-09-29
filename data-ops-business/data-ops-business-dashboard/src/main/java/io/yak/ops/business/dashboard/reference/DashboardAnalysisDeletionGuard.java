package io.yak.ops.business.dashboard.reference;

import io.yak.ops.business.analysis.AnalysisDeletionGuard;
import io.yak.ops.business.dashboard.repository.DashboardReferenceRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.springframework.stereotype.Component;

/** Preserves the historical-version Analysis reference RESTRICT rule. */
@Component
@ConditionalOnDataSourceEnabled
public class DashboardAnalysisDeletionGuard implements AnalysisDeletionGuard {

  private final DashboardReferenceRepository references;

  public DashboardAnalysisDeletionGuard(DashboardReferenceRepository references) {
    this.references = references;
  }

  @Override
  public void requireDeletable(long analysisId) {
    if (references.existsAnalysisReference(analysisId)) {
      throw new IllegalStateException(
          "Analysis 仍被 Dashboard 历史版本引用，不能删除：" + analysisId);
    }
  }
}
