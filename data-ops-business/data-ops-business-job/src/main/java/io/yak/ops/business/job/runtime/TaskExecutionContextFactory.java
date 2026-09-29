package io.yak.ops.business.job.runtime;

import io.yak.ops.business.job.environment.TaskEnvironmentResolver;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.plugin.task.api.DefaultTaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Creates runtime contexts without depending on environment-management CRUD. */
@Component
public class TaskExecutionContextFactory {

  private final TaskEnvironmentResolver environmentResolver;
  private final CurrentProject currentProject;
  private final ProjectContextScope projectScope;

  /** Keeps focused tests and existing non-Spring callers source compatible. */
  public TaskExecutionContextFactory(TaskEnvironmentResolver environmentResolver) {
    this(environmentResolver, null, null);
  }

  @Autowired
  public TaskExecutionContextFactory(
      TaskEnvironmentResolver environmentResolver,
      CurrentProject currentProject,
      ProjectContextScope projectScope) {
    this.environmentResolver = environmentResolver;
    this.currentProject = currentProject;
    this.projectScope = projectScope;
  }

  public TaskExecutionContext create(
      TaskExecutionTrigger trigger,
      Map<String, Object> input) {
    return DefaultTaskExecutionContext.builder()
        .trigger(trigger)
        .parameters(input)
        .globalEnvVars(environmentResolver.resolveMergedEnv())
        .build();
  }

  public TaskExecutionContext create(
      TaskExecutionTrigger trigger,
      Map<String, Object> input,
      java.util.function.Consumer<DefaultTaskExecutionContext.Builder> customiser) {
    DefaultTaskExecutionContext.Builder builder = DefaultTaskExecutionContext.builder()
        .trigger(trigger)
        .parameters(input)
        .globalEnvVars(environmentResolver.resolveMergedEnv());
    if (customiser != null) {
      customiser.accept(builder);
    }
    return builder.build();
  }

  /**
   * Captures the trusted Project Space on the submitting thread and restores it when the returned
   * work runs on an asynchronous worker.
   */
  Runnable captureProjectContext(Runnable action) {
    Objects.requireNonNull(action, "action must not be null");
    if (currentProject == null || projectScope == null) return action;

    ProjectContext captured = currentProject.current().orElse(null);
    if (captured == null) return action;
    return () -> projectScope.run(captured, action);
  }
}
