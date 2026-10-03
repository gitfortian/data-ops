package io.yak.ops.business.job.repository;

import io.yak.ops.business.job.task.TaskExecution;
import java.util.Optional;

/** Project-scoped terminal evidence, independent of live plugin handles and business definitions. */
public interface TaskExecutionJournal {
  void save(String taskType, String idempotencyKey, TaskExecution execution);
  Optional<TaskExecution> findById(String executionId);
  Optional<TaskExecution> findByKey(String taskType, String idempotencyKey);
}
