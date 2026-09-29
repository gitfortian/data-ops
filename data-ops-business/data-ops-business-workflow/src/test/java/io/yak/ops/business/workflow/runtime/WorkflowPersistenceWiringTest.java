package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.observability.WorkflowEventStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class WorkflowPersistenceWiringTest {

  @Test
  void runtimeShouldFailFastWhenDatabaseIsEnabledButRepositoriesAreMissing() {
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    assertThatThrownBy(() -> new WorkflowRuntime(
        new WorkflowEventStream(),
        mock(TaskRegistry.class),
        mock(TaskExecutionGateway.class),
        provider(beans, WorkflowDefinitionRepository.class),
        provider(beans, ExecutionRepository.class),
        provider(beans, WorkflowRuntimeRepository.class),
        true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WorkflowDefinitionRepository");
  }

  @Test
  void definitionServiceShouldFailFastWhenDatabaseIsEnabledButRepositoriesAreMissing() {
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    assertThatThrownBy(() -> new WorkflowDefinitionManager(
        mock(WorkflowRuntime.class),
        mock(TaskRegistry.class),
        provider(beans, TaskCatalogService.class),
        new ObjectMapper(),
        provider(
            beans,
            io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository.class),
        provider(beans, WorkflowExecutionDao.class),
        mock(CurrentProject.class),
        true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WorkflowDefinitionRepository");
  }

  @Test
  void runtimeShouldAllowExplicitDatabaseDisabledMode() {
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    WorkflowRuntime runtime = new WorkflowRuntime(
        new WorkflowEventStream(),
        mock(TaskRegistry.class),
        mock(TaskExecutionGateway.class),
        provider(beans, WorkflowDefinitionRepository.class),
        provider(beans, ExecutionRepository.class),
        provider(beans, WorkflowRuntimeRepository.class),
        false);
    try {
      // Construction itself proves the explicit in-memory development/test path remains available.
    } finally {
      runtime.shutdown();
    }
  }

  private static <T> ObjectProvider<T> provider(
      DefaultListableBeanFactory beans,
      Class<T> type) {
    return beans.getBeanProvider(type);
  }
}
