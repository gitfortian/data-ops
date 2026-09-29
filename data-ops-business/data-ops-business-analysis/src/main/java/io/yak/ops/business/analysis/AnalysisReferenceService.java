package io.yak.ops.business.analysis;

import io.yak.ops.business.analysis.reference.AnalysisReferenceReader;
import org.springframework.stereotype.Service;

/** Stable narrow cross-domain facade for assets that only hold an Analysis reference. */
@Service
public class AnalysisReferenceService {

  private final AnalysisReferenceReader references;

  public AnalysisReferenceService(AnalysisReferenceReader references) {
    this.references = references;
  }

  public void requireExists(long analysisId) {
    references.requireExists(analysisId);
  }
}
