package io.yak.ops.business.development.execution.model;

import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.util.Objects;

/** Immediate acknowledgement returned after a manual editor execution is accepted by Task Runtime. */
public record DevelopmentTaskExecutionSubmission(
    Long id,
    Long nodeId,
    String taskType,
    String runtimeExecutionId,
    TaskExecutionStatus status) {

  public DevelopmentTaskExecutionSubmission {
    id = Objects.requireNonNull(id, "id");
    nodeId = Objects.requireNonNull(nodeId, "nodeId");
    taskType = Objects.requireNonNull(taskType, "taskType");
    status = Objects.requireNonNull(status, "status");
  }
}
