package io.yak.ops.spi.task.model;

/** Common lifecycle state for one physical task execution. */
public enum TaskExecutionStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED,
  CANCELLED,
  TIMEOUT
}
