package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Narrow persistence boundary for cross-instance Dataset query diagnostics. */
public interface DatasetQueryPerformanceStore {

  void append(Long projectId, DatasetQueryPerformance trace);

  List<DatasetQueryPerformance> recent(
      Long projectId,
      Set<Long> datasetIds,
      Set<String> queryIds,
      Set<DatasetQueryStatus> statuses,
      Long minTotalMillis,
      int limit);

  int deleteBefore(Instant cutoff, int limit);
}
