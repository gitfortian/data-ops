package io.yak.ops.business.sync.offline.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** ExecutionAttempt 持久化兼容仓储；运行占用真相只允许从 BatchExecution 仓储读取。 */
public interface OfflineJobExecutionRepository {
  Optional<OfflineJobExecution> findById(Long id);
  Optional<OfflineJobExecution> findByIdempotencyKey(String idempotencyKey);
  List<OfflineJobExecution> findByBatchId(Long batchId);
  boolean insert(OfflineJobExecution execution);
  boolean update(OfflineJobExecution execution);
  /** Project-scoped active Attempt query. */
  List<OfflineJobExecution> findActiveExecutions(int limit);
  /** Cross-Project dispatcher identities; restore Project before business IO. */
  List<ProjectExecutionRef> findActiveExecutionsForReconciliation(int limit);
  /** Project-scoped retry candidate query. */
  List<OfflineJobExecution> findRetryCandidates(LocalDateTime now, int limit);
  /** Cross-Project retry dispatcher identities; restore Project before business IO. */
  List<ProjectExecutionRef> findRetryCandidatesForReconciliation(LocalDateTime now, int limit);
  /** 原子保留一次 FAILED Attempt 的 Retry 创建权。 */
  boolean reserveRetry(Long executionId);
  PageData<OfflineJobExecution> page(OfflineExecutionQuery query);

  record ProjectExecutionRef(long projectId, long executionId) {
    public ProjectExecutionRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId 必须大于 0");
      if (executionId <= 0L) throw new IllegalArgumentException("executionId 必须大于 0");
    }
  }
}
