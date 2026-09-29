package io.yak.ops.business.analysis.dao;

import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import java.util.List;
import java.util.Optional;

/** Database-facing contract. Only persistence models are exposed here. */
public interface AnalysisDao {

  long insert(AnalysisPO value);

  void update(long analysisId, AnalysisPO value);

  Optional<AnalysisPO> findById(long analysisId);

  List<AnalysisPO> list();

  void delete(long analysisId);
}
