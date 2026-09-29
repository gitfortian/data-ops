package io.yak.ops.plugin.task.api;

import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.util.Map;
import java.util.Objects;

/** Result returned by one physical task execution attempt. */
public record TaskExecutionResult(
    TaskExecutionStatus status,
    String message,
    Map<String, Object> output) {

  public TaskExecutionResult {
    status = Objects.requireNonNull(status, "status");
    message = message == null ? "" : message;
    output = output == null ? Map.of() : Map.copyOf(output);
  }

  public static TaskExecutionResult success(Map<String, Object> output) {
    return new TaskExecutionResult(TaskExecutionStatus.SUCCESS, "", output);
  }
}
