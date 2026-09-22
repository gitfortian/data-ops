package io.yak.ops.business.dataset.definition;

import io.yak.ops.business.dataset.DatasetOverviewSnapshot;
import io.yak.ops.business.dataset.repository.DatasetOverviewRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bounded read-side projection used by overview surfaces. */
@Component
public class DatasetOverviewReader {

  private static final int MAX_LIST_LIMIT = 20;

  private final DatasetOverviewRepository repository;

  public DatasetOverviewReader(DatasetOverviewRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public DatasetOverviewSnapshot overview(Instant from, Instant to, int listLimit) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("Dataset overview 时间范围无效");
    }
    int limit = Math.max(1, Math.min(MAX_LIST_LIMIT, listLimit));
    DatasetOverviewRepository.Summary summary = repository.summarize(from, to);
    return new DatasetOverviewSnapshot(
        summary.datasetCount(),
        summary.createdCount(),
        repository.listRecent(limit),
        repository.listRecentOnline(limit));
  }
}
