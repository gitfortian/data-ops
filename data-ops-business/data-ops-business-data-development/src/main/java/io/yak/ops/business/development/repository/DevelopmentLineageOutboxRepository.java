package io.yak.ops.business.development.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence contract for durable SQL-lineage outbox work. */
public interface DevelopmentLineageOutboxRepository {

  void enqueue(String taskId, long nodeId, long revisionId);

  List<OutboxRecord> due(int limit);

  boolean claim(OutboxRecord record);

  void complete(OutboxRecord record);

  void fail(OutboxRecord record, String errorMessage, long delaySeconds);

  /** Project-scoped diagnostic view for one published Development revision. */
  Optional<DiagnosticRecord> findDiagnostic(long nodeId, long revisionId);

  record OutboxRecord(
      String taskId,
      Long projectId,
      long nodeId,
      long revisionId,
      int attempts) {
    public OutboxRecord {
      if (projectId == null || projectId <= 0L) {
        throw new IllegalArgumentException("lineage outbox projectId must be positive");
      }
    }
  }

  record DiagnosticRecord(
      String taskId,
      long nodeId,
      long revisionId,
      String status,
      int attempts,
      String lastError,
      LocalDateTime nextAttemptTime,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}
}
