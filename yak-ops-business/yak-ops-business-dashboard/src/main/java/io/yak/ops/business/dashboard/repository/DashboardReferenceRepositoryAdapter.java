package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.dao.DashboardDao;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.springframework.stereotype.Repository;

/** MyBatis read adapter for cross-domain Dashboard references. */
@Repository
@ConditionalOnDataSourceEnabled
public class DashboardReferenceRepositoryAdapter implements DashboardReferenceRepository {

  private final DashboardDao dao;

  public DashboardReferenceRepositoryAdapter(DashboardDao dao) {
    this.dao = dao;
  }

  @Override
  public boolean existsAnalysisReference(long analysisId) {
    return dao.existsWidgetByAnalysisId(analysisId);
  }
}
