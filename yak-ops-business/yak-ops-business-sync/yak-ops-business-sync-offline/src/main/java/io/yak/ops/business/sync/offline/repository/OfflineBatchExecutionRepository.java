package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import java.util.List;
import java.util.Optional;

/** 离线同步业务批次领域仓储。 */
public interface OfflineBatchExecutionRepository {

  Optional<BatchExecution> findById(Long id);

  Optional<BatchExecution> findByTaskIdAndBatchKey(long taskId, BatchKey batchKey);

  /** V1 Task runtime truth：RUNNING / WAITING_RETRY / UNKNOWN Batch 会占用任务执行槽位。 */
  boolean hasOccupyingBatch(long taskId);

  /** 返回任务最近一个占用执行槽位的 Batch；命令判断不得回退到 Task last-*。 */
  Optional<BatchExecution> findLatestOccupyingByTaskId(long taskId);

  /** Project-scoped PENDING Backfill query. */
  List<BatchExecution> findPendingBackfills(int limit);

  /** Cross-Project dispatcher identity only; restore Project before loading the Batch. */
  List<ProjectBatchRef> findPendingBackfillsForDispatch(int limit);

  /** PENDING -> RUNNING CAS reservation，防止多节点重复创建 Backfill Attempt 1。 */
  boolean reservePendingBackfill(long batchId);

  BatchExecution insert(BatchExecution batch);

  boolean update(BatchExecution batch);

  record ProjectBatchRef(long projectId, long batchId) {
    public ProjectBatchRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId 必须大于 0");
      if (batchId <= 0L) throw new IllegalArgumentException("batchId 必须大于 0");
    }
  }
}
