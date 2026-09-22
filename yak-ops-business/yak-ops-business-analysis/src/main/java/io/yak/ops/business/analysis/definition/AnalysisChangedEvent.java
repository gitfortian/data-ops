package io.yak.ops.business.analysis.definition;

/** Committed Analysis mutation fact consumed by derived projections such as lineage. */
public record AnalysisChangedEvent(long projectId, long analysisId, boolean deleted) {

  public AnalysisChangedEvent {
    if (projectId <= 0L) throw new IllegalArgumentException("projectId 必须大于 0");
    if (analysisId <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
  }

  public static AnalysisChangedEvent refreshed(long projectId, long analysisId) {
    return new AnalysisChangedEvent(projectId, analysisId, false);
  }

  public static AnalysisChangedEvent deleted(long projectId, long analysisId) {
    return new AnalysisChangedEvent(projectId, analysisId, true);
  }
}
