package io.yak.ops.business.workflow.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.workflow.definition.WorkflowTaskBindingResolver.BindingView;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkflowTaskBindingResolverProjectScopeTest {

  @Test
  void describeFailsClosedWhenProjectIsMissing() {
    TaskRegistry taskRegistry = mock(TaskRegistry.class);
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);
    WorkflowNodeSpec node = catalogNode(101L);
    CurrentProject currentProject = () -> Optional.empty();
    WorkflowTaskBindingResolver resolver =
        new WorkflowTaskBindingResolver(
            taskRegistry, taskCatalogService, new ObjectMapper(), currentProject);

    assertThatThrownBy(() -> resolver.describe(node))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void describeRejectsTaskAssetOwnedByAnotherProject() {
    TaskRegistry taskRegistry = mock(TaskRegistry.class);
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);
    WorkflowNodeSpec node = catalogNode(101L);
    TaskAsset asset = mock(TaskAsset.class);
    when(asset.projectId()).thenReturn(9L);
    when(taskCatalogService.get(101L)).thenReturn(asset);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    WorkflowTaskBindingResolver resolver =
        new WorkflowTaskBindingResolver(
            taskRegistry, taskCatalogService, new ObjectMapper(), currentProject);

    assertThatThrownBy(() -> resolver.describe(node))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void describeKeepsOrdinaryCatalogFailuresAsUnresolvedBindings() {
    TaskRegistry taskRegistry = mock(TaskRegistry.class);
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);
    WorkflowNodeSpec node = catalogNode(101L);
    when(taskCatalogService.get(101L)).thenThrow(new IllegalStateException("asset unavailable"));
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    WorkflowTaskBindingResolver resolver =
        new WorkflowTaskBindingResolver(
            taskRegistry, taskCatalogService, new ObjectMapper(), currentProject);

    BindingView binding = resolver.describe(node);

    assertThat(binding.taskAssetStatus()).isEqualTo("UNKNOWN");
    assertThat(binding.taskAssetName()).isNull();
  }

  private WorkflowNodeSpec catalogNode(long taskAssetId) {
    WorkflowNodeSpec node = mock(WorkflowNodeSpec.class);
    when(node.catalogBound()).thenReturn(true);
    when(node.taskAssetId()).thenReturn(taskAssetId);
    when(node.taskRevisionId()).thenReturn(201L);
    when(node.taskRevisionNo()).thenReturn(1);
    return node;
  }
}
