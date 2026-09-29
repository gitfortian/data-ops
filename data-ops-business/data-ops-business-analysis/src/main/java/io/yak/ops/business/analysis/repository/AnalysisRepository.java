package io.yak.ops.business.analysis.repository;

import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.domain.AnalysisDefinition;
import java.util.List;
import java.util.Optional;

/** Domain repository. Persistence rows and JSON strings must not cross this boundary. */
public interface AnalysisRepository {

  long insert(AnalysisDefinition definition);

  void update(long analysisId, AnalysisDefinition definition);

  Optional<AnalysisAsset> findById(long analysisId);

  List<AnalysisAsset> list();

  void delete(long analysisId);
}
