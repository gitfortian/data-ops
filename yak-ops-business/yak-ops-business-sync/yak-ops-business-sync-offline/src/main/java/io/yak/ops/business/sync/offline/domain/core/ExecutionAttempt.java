package io.yak.ops.business.sync.offline.domain.core;

import java.time.LocalDateTime;
import java.util.Objects;

/** One concrete submission attempt inside a BatchExecution. */
public record ExecutionAttempt(
    Long id,
    int attemptNo,
    AttemptReason reason,
    String idempotencyKey,
    String externalExecutionId,
    AttemptStatus status,
    EngineExecutionRef engineExecutionRef,
    AttemptMetrics metrics,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {

  public ExecutionAttempt {
    if (id != null && id <= 0) throw new IllegalArgumentException("AttemptId 必须大于 0");
    if (attemptNo < 1) throw new IllegalArgumentException("attemptNo 必须大于 0");
    reason = Objects.requireNonNull(reason, "reason 不能为空");
    idempotencyKey = requireText(idempotencyKey, "idempotencyKey 不能为空");
    externalExecutionId = requireText(externalExecutionId, "externalExecutionId 不能为空");
    status = Objects.requireNonNull(status, "status 不能为空");
    metrics = Objects.requireNonNull(metrics, "metrics 不能为空");
    createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
  }

  private static String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
