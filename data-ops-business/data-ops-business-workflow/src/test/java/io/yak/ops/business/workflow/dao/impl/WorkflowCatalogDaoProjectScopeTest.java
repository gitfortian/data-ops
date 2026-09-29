package io.yak.ops.business.workflow.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowVersionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowCatalogDaoProjectScopeTest {

  @Mock private WorkflowDefinitionMapper definitionMapper;
  @Mock private WorkflowVersionMapper versionMapper;
  @Mock private WorkflowScheduleMapper scheduleMapper;

  @Test
  void upsertBindsDefinitionToCurrentProject() {
    when(definitionMapper.upsert(any(WorkflowDefinitionPO.class))).thenReturn(1);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    WorkflowCatalogDaoImpl dao =
        new WorkflowCatalogDaoImpl(
            definitionMapper, versionMapper, scheduleMapper, currentProject);
    WorkflowDefinitionPO definition = new WorkflowDefinitionPO();
    definition.setId("wf-1");
    definition.setName("daily-orders");

    assertThat(dao.upsertDefinition(definition)).isEqualTo(1);

    ArgumentCaptor<WorkflowDefinitionPO> captor =
        ArgumentCaptor.forClass(WorkflowDefinitionPO.class);
    org.mockito.Mockito.verify(definitionMapper).upsert(captor.capture());
    assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
  }

  @Test
  void upsertRejectsExistingDefinitionFromAnotherProject() {
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    WorkflowCatalogDaoImpl dao =
        new WorkflowCatalogDaoImpl(
            definitionMapper, versionMapper, scheduleMapper, currentProject);
    WorkflowDefinitionPO existing = new WorkflowDefinitionPO();
    existing.setId("wf-1");
    existing.setProjectId(9L);
    when(definitionMapper.selectById("wf-1")).thenReturn(existing);
    WorkflowDefinitionPO update = new WorkflowDefinitionPO();
    update.setId("wf-1");

    assertThatThrownBy(() -> dao.upsertDefinition(update))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void insertRuntimeVersionBindsProjectWithoutParentWorkflow() {
    when(versionMapper.insert(any(WorkflowVersionPO.class))).thenReturn(1);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    WorkflowCatalogDaoImpl dao =
        new WorkflowCatalogDaoImpl(
            definitionMapper, versionMapper, scheduleMapper, currentProject);
    WorkflowVersionPO runtime = new WorkflowVersionPO();
    runtime.setId("workflow-runtime-1");
    runtime.setVersionKind("RUNTIME");

    assertThat(dao.insertVersion(runtime)).isEqualTo(1);

    ArgumentCaptor<WorkflowVersionPO> captor =
        ArgumentCaptor.forClass(WorkflowVersionPO.class);
    org.mockito.Mockito.verify(versionMapper).insert(captor.capture());
    assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
    assertThat(captor.getValue().getWorkflowId()).isNull();
  }

  @Test
  void insertPublishedVersionRequiresAccessibleParentWorkflow() {
    when(definitionMapper.selectCount(any())).thenReturn(0L);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    WorkflowCatalogDaoImpl dao =
        new WorkflowCatalogDaoImpl(
            definitionMapper, versionMapper, scheduleMapper, currentProject);
    WorkflowVersionPO version = new WorkflowVersionPO();
    version.setId("workflow-version-9");
    version.setWorkflowId("wf-9");
    version.setVersionKind("PUBLISHED");

    assertThatThrownBy(() -> dao.insertVersion(version))
        .isInstanceOf(ProjectContextException.class);
  }
}
