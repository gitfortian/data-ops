package io.yak.ops.business.dashboard.read;

import io.yak.ops.business.dashboard.domain.DashboardOverview;
import io.yak.ops.business.dashboard.repository.DashboardOverviewRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bounded Dashboard read projection used by overview surfaces. */
@Component
public class DashboardOverviewReader {

  private static final int MAX_LIST_LIMIT = 20;

  private final DashboardOverviewRepository repository;

  public DashboardOverviewReader(DashboardOverviewRepository repository) {
    this.repository = repository;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public DashboardOverview overview(int listLimit) {
    int limit = Math.max(1, Math.min(MAX_LIST_LIMIT, listLimit));
    DashboardOverviewRepository.Summary summary = repository.summarize();
    return new DashboardOverview(
        summary.dashboardCount(),
        summary.publishedDashboardCount(),
        repository.listRecent(limit));
  }
}
