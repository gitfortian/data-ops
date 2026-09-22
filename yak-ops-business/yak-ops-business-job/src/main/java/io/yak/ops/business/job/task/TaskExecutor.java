package io.yak.ops.business.job.task;

import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;

/**
 * Executes one immutable task snapshot.
 *
 * <p>Workflow only depends on this contract. SQL, SYNC, HTTP, Shell and future task types plug in
 * independently without leaking task-specific configuration into the workflow runtime.</p>
 */
public interface TaskExecutor {

  String taskType();

  TaskExecution start(
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      Map<String, Object> input);

  /**
   * Trigger-aware runtime entry used by manual, workflow and schedule callers.
   *
   * <p>The default keeps existing executors source-compatible. Executors that need trigger-specific
   * behavior can override this method while the legacy start method continues to represent a
   * workflow-triggered execution.</p>
   */
  default TaskExecution start(
      TaskVersionSnapshot snapshot,
      TaskExecutionTrigger trigger,
      String idempotencyKey,
      Map<String, Object> input) {
    return start(snapshot, idempotencyKey, input);
  }

  TaskExecution status(String executionId);

  void cancel(String executionId);
}
