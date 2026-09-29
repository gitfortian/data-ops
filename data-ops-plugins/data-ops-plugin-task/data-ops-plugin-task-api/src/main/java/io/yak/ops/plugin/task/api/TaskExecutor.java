package io.yak.ops.plugin.task.api;

/** Fresh executor instance used for one physical task execution attempt. */
public interface TaskExecutor {

  TaskExecutionResult execute() throws Exception;

  default void cancel() {
    // Optional capability. Plugins that advertise cancellable=true must override this method.
  }
}
