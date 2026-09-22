package io.yak.ops.business.development.repository;

import java.util.List;

/** Persistence contract for durable SQL-lineage outbox work. */
public interface DevelopmentLineageOutboxRepository {

  void enqueue(String taskId, long nodeId, long revisionId);

  List<OutboxRecord> due(int limit);

  boolean claim(OutboxRecord record);

  void complete(OutboxRecord record);

  void fail(OutboxRecord record, String errorMessage, long delaySeconds);

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
}
