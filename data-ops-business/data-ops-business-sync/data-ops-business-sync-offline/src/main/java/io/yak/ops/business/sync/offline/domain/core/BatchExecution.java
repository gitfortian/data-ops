package io.yak.ops.business.sync.offline.domain.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Aggregate root for one business batch. */
public record BatchExecution(
    Long id,
    long taskId,
    BatchKey batchKey,
    BatchTrigger trigger,
    BatchScope batchScope,
    ExecutionSnapshot snapshot,
    BatchStatus status,
    List<ExecutionAttempt> attempts) {

  public BatchExecution {
    if (id != null && id <= 0) throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    if (taskId <= 0) throw new IllegalArgumentException("TaskId 必须大于 0");
    batchKey = Objects.requireNonNull(batchKey, "BatchKey 不能为空");
    trigger = Objects.requireNonNull(trigger, "BatchTrigger 不能为空");
    batchScope = Objects.requireNonNull(batchScope, "BatchScope 不能为空");
    snapshot = Objects.requireNonNull(snapshot, "ExecutionSnapshot 不能为空");
    status = Objects.requireNonNull(status, "BatchStatus 不能为空");
    attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts 不能为空"));
    for (int index = 0; index < attempts.size(); index++) {
      ExecutionAttempt attempt = Objects.requireNonNull(attempts.get(index), "Attempt 不能为空");
      int expected = index + 1;
      if (attempt.attemptNo() != expected) {
        throw new IllegalArgumentException("AttemptNo 必须从 1 开始连续递增");
      }
    }
  }

  public Optional<ExecutionAttempt> latestAttempt() {
    return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.get(attempts.size() - 1));
  }
}
