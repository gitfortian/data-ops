package io.yak.ops.business.development.execution.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.util.Objects;

/** Immediate acknowledgement returned after a manual editor execution is accepted by Task Runtime. */
public record DevelopmentTaskExecutionSubmission(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    @JsonSerialize(using = ToStringSerializer.class) Long nodeId,
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
