package io.yak.ops.business.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.workflow.observability.WorkflowEventStream;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class WorkflowRuntimeSpringTest {

  @Test
  void shouldCreateRuntimeServiceThroughSpring() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(
          new MapPropertySource("workflow-test", Map.of("yak.database.enabled", "false")));
      context.getBeanFactory().registerSingleton("taskRegistry", mock(TaskRegistry.class));
      context.getBeanFactory().registerSingleton(
          "taskExecutionGateway", mock(TaskExecutionGateway.class));
      context.getBeanFactory().registerSingleton(
          "projectContextRuntime", new TestProjectContextRuntime());
      context.register(WorkflowEventStream.class, WorkflowRuntime.class);
      context.refresh();

      assertThat(context.getBean(WorkflowRuntime.class)).isNotNull();
    }
  }

  private static final class TestProjectContextRuntime
      implements CurrentProject, ProjectContextScope {

    @Override
    public Optional<ProjectContext> current() {
      return Optional.empty();
    }

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      return action.get();
    }
  }
}
