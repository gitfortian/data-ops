package io.yak.ops.business.workflow.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeAttemptMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowVersionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
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
class WorkflowExecutionDaoProjectScopeTest {

  @Mock private WorkflowExecutionMapper executionMapper;
  @Mock private WorkflowNodeExecutionMapper nodeExecutionMapper;
  @Mock private WorkflowNodeAttemptMapper nodeAttemptMapper;
  @Mock private WorkflowDefinitionMapper definitionMapper;
  @Mock private WorkflowVersionMapper versionMapper;

  @Test
  void upsertBindsExecutionToCurrentProjectAndOwnedDefinition() {
    WorkflowExecutionDaoImpl dao = dao(7L);
    WorkflowVersionPO version = new WorkflowVersionPO();
    version.setId("workflow-runtime-1");
    version.setProjectId(7L);
    when(versionMapper.selectByIdAndProject("workflow-runtime-1", 7L)).thenReturn(version);
    when(executionMapper.upsert(any(WorkflowExecutionPO.class))).thenReturn(1);
    WorkflowExecutionPO execution = new WorkflowExecutionPO();
    execution.setId("execution-1");
    execution.setDefinitionId("workflow-runtime-1");

    assertThat(dao.upsertExecution(execution)).isEqualTo(1);

    ArgumentCaptor<WorkflowExecutionPO> captor =
        ArgumentCaptor.forClass(WorkflowExecutionPO.class);
    org.mockito.Mockito.verify(executionMapper).upsert(captor.capture());
    assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
  }

  @Test
  void upsertRejectsDefinitionOutsideCurrentProject() {
    WorkflowExecutionDaoImpl dao = dao(7L);
    when(versionMapper.selectByIdAndProject("workflow-runtime-9", 7L)).thenReturn(null);
    WorkflowExecutionPO execution = new WorkflowExecutionPO();
    execution.setId("execution-1");
    execution.setDefinitionId("workflow-runtime-9");

    assertThatThrownBy(() -> dao.upsertExecution(execution))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void upsertRejectsExistingExecutionFromAnotherProject() {
    WorkflowExecutionDaoImpl dao = dao(7L);
    WorkflowExecutionPO existing = new WorkflowExecutionPO();
    existing.setId("execution-1");
    existing.setProjectId(9L);
    when(executionMapper.selectById("execution-1")).thenReturn(existing);
    WorkflowExecutionPO execution = new WorkflowExecutionPO();
    execution.setId("execution-1");
    execution.setDefinitionId("workflow-runtime-1");

    assertThatThrownBy(() -> dao.upsertExecution(execution))
        .isInstanceOf(ProjectContextException.class);
  }

  private WorkflowExecutionDaoImpl dao(long projectId) {
    CurrentProject currentProject =
        () -> Optional.of(new ProjectContext(projectId, "Project " + projectId));
    return new WorkflowExecutionDaoImpl(
        executionMapper,
        nodeExecutionMapper,
        nodeAttemptMapper,
        definitionMapper,
        versionMapper,
        currentProject);
  }
}
