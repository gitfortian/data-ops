package io.yak.ops.business.sync.offline.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.time.LocalDateTime;
import java.util.List;

/** ExecutionAttempt 持久化 DAO；不提供 Task 级 runtime truth 查询。 */
public interface OfflineJobExecutionDao {

  OfflineJobExecutionPO selectById(Long id);

  OfflineJobExecutionPO selectByIdempotencyKey(String idempotencyKey);

  List<OfflineJobExecutionPO> selectByBatchId(Long batchId);

  boolean insert(OfflineJobExecutionPO executionPO);

  boolean updateById(OfflineJobExecutionPO executionPO);

  /** Project-scoped active Attempt query. */
  List<OfflineJobExecutionPO> selectActiveExecutions(int limit);

  /** Explicit cross-Project reconcile scan. */
  List<OfflineJobExecutionPO> selectActiveExecutionsForReconciliation(int limit);

  /** Project-scoped Retry candidate query. */
  List<OfflineJobExecutionPO> selectRetryCandidates(LocalDateTime now, int limit);

  /** Explicit cross-Project retry dispatcher scan. */
  List<OfflineJobExecutionPO> selectRetryCandidatesForReconciliation(LocalDateTime now, int limit);

  /** 原子保留一次 FAILED Attempt 的 Retry 创建权。 */
  boolean reserveRetry(Long executionId, LocalDateTime updateTime);

  IPage<OfflineJobExecutionPO> selectPage(PageQuery query);

  record PageQuery(
      int current,
      int pageSize,
      Long jobDefinitionId,
      String status) {}
}
