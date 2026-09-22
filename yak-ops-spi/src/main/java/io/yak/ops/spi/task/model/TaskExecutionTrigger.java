package io.yak.ops.spi.task.model;

/** Entry point that caused one task execution. */
public enum TaskExecutionTrigger {
  MANUAL,
  WORKFLOW,
  SCHEDULE
}
