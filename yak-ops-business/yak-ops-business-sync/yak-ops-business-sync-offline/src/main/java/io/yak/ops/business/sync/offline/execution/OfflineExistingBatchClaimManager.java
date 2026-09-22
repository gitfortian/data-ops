package io.yak.ops.business.sync.offline.execution;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.BatchTriggerToken;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns Attempt admission on an existing Batch for Retry and pending Backfill. */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineExistingBatchClaimManager {

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineBatchRuntime batchRuntime;
  private final OfflineExecutionAttemptFactory attemptFactory;

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineExecutionClaim claimRetry(Long retryFromExecutionId) {
    if (retryFromExecutionId == null || retryFromExecutionId <= 0L) {
      throw new IllegalArgumentException("重试来源实例不能为空");
    }

    OfflineJobExecution previous =
        executionRepository
            .findById(retryFromExecutionId)
            .orElseThrow(
                () -> new IllegalArgumentException("重试来源实例不存在：" + retryFromExecutionId));
    BatchExecution batch = requireRetryBatch(previous);
    OfflineExecutionStatus previousStatus = requireRetryableStatus(previous);
    if (previousStatus != OfflineExecutionStatus.FAILED) {
      throw new IllegalStateException("只有明确 FAILED 的 Attempt 才能 Retry");
    }

    List<OfflineJobExecution> attempts = executionRepository.findByBatchId(batch.id());
    if (attempts.isEmpty()) {
      throw new IllegalStateException("Batch 缺少 Attempt 历史，不能 Retry");
    }

    int previousAttemptNo = positive(previous.getAttemptNo(), "attemptNo");
    int nextAttemptNo = previousAttemptNo + 1;
    OfflineJobExecution existingNext = findRetryAttempt(attempts, previous, nextAttemptNo);
    if (existingNext != null) {
      batchRuntime.refreshBatch(batch.id());
      return reused(batch, existingNext);
    }

    OfflineJobExecution latest = latestAttempt(attempts);
    if (!Objects.equals(latest.getId(), previous.getId())) {
      throw new IllegalStateException("只能从 Batch 最新 Attempt 创建 Retry");
    }

    RetryPolicySnapshot retryPolicy = batch.snapshot().retryPolicy();
    if (nextAttemptNo > retryPolicy.maxAttempts()) {
      throw new IllegalStateException("Retry 已达到 Batch 冻结的最大 Attempt 数");
    }
    if (!executionRepository.reserveRetry(previous.getId())) {
      throw new IllegalStateException("Retry 已被其他请求保留或已经创建");
    }

    OfflineJobExecution execution =
        attemptFactory.create(
            batch,
            nextAttemptNo,
            BatchTriggerToken.RETRY,
            previous.getId(),
            "offline-retry:" + batch.id() + ":" + nextAttemptNo);
    persistNewAttempt(execution, "创建 Retry Attempt 失败");
    batchRuntime.refreshBatch(batch.id());
    return created(batch, execution);
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineExecutionClaim claimPendingBackfill(Long batchId) {
    if (batchId == null || batchId <= 0L) {
      throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    }

    BatchExecution initial =
        batchRepository
            .findById(batchId)
            .orElseThrow(
                () -> new IllegalArgumentException("Backfill BatchExecution 不存在：" + batchId));
    if (initial.trigger() != BatchTrigger.BACKFILL) {
      throw new IllegalStateException("只有 BACKFILL Batch 可以通过 pending dispatcher 创建 Attempt 1");
    }

    definitionRepository.lock(initial.taskId());
    BatchExecution batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(
                () -> new IllegalStateException("Backfill BatchExecution 已不存在：" + batchId));
    List<OfflineJobExecution> attempts = executionRepository.findByBatchId(batchId);
    OfflineJobExecution existingInitial = findAttempt(attempts, 1);
    if (existingInitial != null) {
      batchRuntime.refreshBatch(batchId);
      return reused(batch, existingInitial);
    }

    requirePendingBackfillSlot(batch);
    if (!batchRepository.reservePendingBackfill(batchId)) {
      throw new IllegalStateException("Backfill Batch 已被其他 dispatcher reservation");
    }

    OfflineJobExecution execution =
        attemptFactory.create(
            batch,
            1,
            BatchTriggerToken.BACKFILL,
            null,
            "offline-backfill:" + batchId + ":1");
    persistNewAttempt(execution, "创建 Backfill Attempt 1 失败");
    batchRuntime.refreshBatch(batchId);
    return created(batch, execution);
  }

  private BatchExecution requireRetryBatch(OfflineJobExecution previous) {
    Long batchId = previous.getBatchId();
    if (batchId == null || batchId <= 0L) {
      throw new IllegalStateException("历史执行未绑定 Batch，仅支持查询，不能按当前领域规则 Retry");
    }

    BatchExecution batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(
                () -> new IllegalStateException("Retry 所属 BatchExecution 不存在：" + batchId));
    if (!Objects.equals(previous.getJobDefinitionId(), batch.taskId())) {
      throw new IllegalStateException("Retry 来源 Attempt 与 Batch 的 Task 不一致");
    }
    if (batch.status().isTerminal()) {
      throw new IllegalStateException("Batch 已进入终态，不能追加 Retry Attempt");
    }
    return batch;
  }

  private OfflineExecutionStatus requireRetryableStatus(OfflineJobExecution previous) {
    OfflineExecutionStatus status;
    try {
      status = OfflineExecutionStatus.parse(previous.getStatus());
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Retry 来源 Attempt 状态不合法：" + previous.getStatus(), exception);
    }
    if (status == OfflineExecutionStatus.UNKNOWN) {
      throw new IllegalStateException("执行结果为 UNKNOWN，必须先 reconcile，禁止盲目 Retry");
    }
    return status;
  }

  private OfflineJobExecution findRetryAttempt(
      List<OfflineJobExecution> attempts, OfflineJobExecution previous, int attemptNo) {
    return attempts.stream()
        .filter(attempt -> value(attempt.getAttemptNo(), 1) == attemptNo)
        .filter(attempt -> Objects.equals(attempt.getRetryFromExecutionId(), previous.getId()))
        .findFirst()
        .orElse(null);
  }

  private OfflineJobExecution findAttempt(List<OfflineJobExecution> attempts, int attemptNo) {
    return attempts.stream()
        .filter(attempt -> value(attempt.getAttemptNo(), 1) == attemptNo)
        .findFirst()
        .orElse(null);
  }

  private OfflineJobExecution latestAttempt(List<OfflineJobExecution> attempts) {
    return attempts.stream()
        .max(
            Comparator.comparingInt(
                    (OfflineJobExecution attempt) -> value(attempt.getAttemptNo(), 1))
                .thenComparingLong(attempt -> value(attempt.getId(), 0L)))
        .orElseThrow(() -> new IllegalStateException("Batch 缺少最新 Attempt"));
  }

  private void requirePendingBackfillSlot(BatchExecution batch) {
    if (batch.status() != BatchStatus.PENDING) {
      throw new IllegalStateException("Backfill Batch 已不处于 PENDING：" + batch.status());
    }
    if (batchRuntime.hasOccupyingBatch(batch.taskId())) {
      throw new IllegalStateException("Task 已有 occupying Batch，Backfill 保持排队");
    }
  }

  private void persistNewAttempt(OfflineJobExecution execution, String message) {
    if (!executionRepository.insert(execution) || execution.getId() == null) {
      throw new IllegalStateException(message);
    }
  }

  private OfflineExecutionClaim reused(BatchExecution batch, OfflineJobExecution execution) {
    return new OfflineExecutionClaim(null, batch.snapshot().logicalJobSpec(), execution, true);
  }

  private OfflineExecutionClaim created(BatchExecution batch, OfflineJobExecution execution) {
    return new OfflineExecutionClaim(null, batch.snapshot().logicalJobSpec(), execution, false);
  }

  private int positive(Integer value, String field) {
    if (value == null || value < 1) {
      throw new IllegalStateException(field + " 必须大于 0");
    }
    return value;
  }

  private int value(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private long value(Long value, long fallback) {
    return value == null ? fallback : value;
  }
}
