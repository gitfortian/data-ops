package io.yak.ops.business.analysis.definition;

import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.repository.AnalysisRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side entry for current reusable Analysis definitions. */
@Component
public class AnalysisReader {

  private final AnalysisRepository repository;

  public AnalysisReader(AnalysisRepository repository) {
    this.repository = repository;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public List<AnalysisAsset> list() {
    return repository.list();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public AnalysisAsset require(long analysisId) {
    requireAnalysisId(analysisId);
    return repository.findById(analysisId)
        .orElseThrow(() -> new IllegalArgumentException("Analysis 不存在：" + analysisId));
  }

  static void requireAnalysisId(long analysisId) {
    if (analysisId <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
  }
}
