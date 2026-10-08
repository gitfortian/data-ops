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

  /** Persisted successful evidence of exactly one immutable DatasetVersion. */
  List<DatasetQueryPerformance> successfulByDatasetAndVersion(
      Long projectId, long datasetId, long datasetVersionId, int limit);

  /** Only persisted SUCCESS traces, exclusively before one durable audit ID. */
  List<io.yak.ops.business.dataset.DatasetSuccessfulQueryAudit> successfulPageByDatasetAndVersion(
      Long projectId, long datasetId, long datasetVersionId, Long beforeAuditId, int limit);

  int deleteBefore(Instant cutoff, int limit);
}
