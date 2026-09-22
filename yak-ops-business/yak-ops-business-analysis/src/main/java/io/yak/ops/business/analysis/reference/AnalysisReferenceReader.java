package io.yak.ops.business.analysis.reference;

import io.yak.ops.business.analysis.definition.AnalysisReader;
import org.springframework.stereotype.Component;

/** Narrow read boundary for downstream assets that only need a stable Analysis reference. */
@Component
public class AnalysisReferenceReader {

  private final AnalysisReader reader;

  public AnalysisReferenceReader(AnalysisReader reader) {
    this.reader = reader;
  }

  public void requireExists(long analysisId) {
    reader.require(analysisId);
  }
}
