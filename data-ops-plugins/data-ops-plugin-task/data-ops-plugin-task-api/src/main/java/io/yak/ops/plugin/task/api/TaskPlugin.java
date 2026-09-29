package io.yak.ops.plugin.task.api;

import io.yak.ops.spi.task.model.TaskDefinition;

/**
 * Stable platform-level task plugin contract.
 *
 * <p>Data development, Workflow and Schedule consume this contract through Task Runtime instead of
 * owning separate SQL/Shell/Python execution implementations.
 */
public interface TaskPlugin {

  TaskPluginDescriptor descriptor();

  default String type() {
    return descriptor().type();
  }

  TaskValidationResult validate(TaskDefinition definition);

  default TaskExecutor createExecutor(
      TaskDefinition definition,
      TaskExecutionContext context) {
    throw new UnsupportedOperationException(
        "Task plugin is not executable yet: " + type());
  }
}
