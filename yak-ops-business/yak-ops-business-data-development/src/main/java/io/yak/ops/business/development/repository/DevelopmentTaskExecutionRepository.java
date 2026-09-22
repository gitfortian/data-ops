package io.yak.ops.business.development.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence contract for durable Data Development manual execution history. */
public interface DevelopmentTaskExecutionRepository {

  long createPending(PendingExecution pending);

  void attachRuntime(long id, String runtimeExecutionId, String status);

  void updateActiveStatus(long id, String status);

  void complete(long id, String status, long durationMs, String errorMessage, String outputJson);

  Page page(Query query);

  Optional<ExecutionRecord> findById(long id);

  Optional<ExecutionRecord> findLatestActiveByNode(long nodeId);

  List<ExecutionRecord> listActiveForReconciliation(int limit);

  record PendingExecution(
      Long projectId,
      long nodeId,
      String taskName,
      String taskType,
      int schemaVersion,
      String operatorName,
      Long retryOfExecutionId,
      String content,
      String configJson) {}

  record Query(
      int pageNo,
      int pageSize,
      String keyword,
      String status,
      String taskType,
      String triggerType,
      LocalDateTime startTime,
      LocalDateTime endTime) {}

  record Page(List<ExecutionRecord> records, long total, int pageNo, int pageSize) {
    public Page {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }

  record ExecutionRecord(
      Long projectId,
      long id,
      long nodeId,
      String taskName,
      String taskType,
      int schemaVersion,
      String triggerType,
      String runtimeExecutionId,
      Long retryOfExecutionId,
      String status,
      String operatorName,
      Long durationMs,
      String errorMessage,
      String content,
      String configJson,
      String outputJson,
      LocalDateTime startTime,
      LocalDateTime endTime) {}
}
