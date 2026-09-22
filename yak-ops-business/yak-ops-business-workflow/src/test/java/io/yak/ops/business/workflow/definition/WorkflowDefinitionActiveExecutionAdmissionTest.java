package io.yak.ops.business.workflow.definition;

import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.repository.NoopWorkflowDefinitionRepository;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionActiveExecutionAdmissionTest {

  @Mock private WorkflowRuntime runtimeService;
  @Mock private TaskRegistry taskRegistry;
  @Mock private WorkflowExecutionDao executionDao;

  private WorkflowDefinitionManager service;

  @BeforeEach
  void setUp() {
    service = new WorkflowDefinitionManager(
        runtimeService,
        taskRegistry,
        NoopWorkflowDefinitionRepository.INSTANCE,
        executionDao);
  }

  @Test
  void shouldRejectManualRunWhenExecutionTableReportsActiveInstance() {
    WorkflowDefinitionVO definition = publishConfiguredWorkflow();
    when(executionDao.countActiveExecutions(definition.id())).thenReturn(1L);

    assertThatThrownBy(() -> service.run(definition.id()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("运行中的执行实例");

    verify(runtimeService, never()).run(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void shouldNotUseLatestExecutionStatusAsConcurrencyFact() {
    WorkflowDefinitionVO definition = publishConfiguredWorkflow();
    when(executionDao.countActiveExecutions(definition.id())).thenReturn(0L);

    WorkflowInstanceVO prepared1 = instance("execution-1", "RUNNING");
    WorkflowInstanceVO running1 = instance("execution-1", "RUNNING");
    WorkflowInstanceVO prepared2 = instance("execution-2", "RUNNING");
    WorkflowInstanceVO running2 = instance("execution-2", "RUNNING");
    when(runtimeService.run(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(prepared1, prepared2);
    when(runtimeService.activate("execution-1")).thenReturn(running1);
    when(runtimeService.activate("execution-2")).thenReturn(running2);
    when(runtimeService.getInstance("execution-1")).thenReturn(running1);
    when(runtimeService.getInstance("execution-2")).thenReturn(running2);

    WorkflowDefinitionVO first = service.run(definition.id());
    WorkflowDefinitionVO second = service.run(definition.id());

    assertThat(first.latestExecutionStatus()).isEqualTo("RUNNING");
    assertThat(second.latestExecutionId()).isEqualTo("execution-2");
  }

  private WorkflowDefinitionVO publishConfiguredWorkflow() {
    WorkflowDefinitionVO created = service.create(
        new WorkflowDefinitionCreateDTO("订单同步", "active execution admission"));
    WorkflowDefinitionVO configured = service.update(
        created.id(),
        new WorkflowDefinitionUpdateDTO(
            created.name(),
            created.description(),
            List.of(new NodeDTO(
                "node-1",
                "sync-1",
                120D,
                80D,
                1,
                0L,
                0L,
                0L,
                Map.of(),
                "ALL_SUCCESS",
                "FAIL_WORKFLOW")),
            List.of(),
            Map.of(),
            0L,
            "CONTINUE_INDEPENDENT_BRANCHES"));
    when(taskRegistry.snapshot("sync-1")).thenReturn(new TaskVersionSnapshot(
        "sync-1",
        "同步订单",
        "SYNC",
        1L,
        "digest-1",
        "{\"definitionVersion\":1}",
        "{\"jobSpecVersion\":1}"));
    return service.online(configured.id());
  }

  private WorkflowInstanceVO instance(String id, String status) {
    Instant now = Instant.parse("2026-08-14T02:00:00Z");
    return new WorkflowInstanceVO(
        id,
        "workflow-version-1",
        null,
        "订单同步",
        status,
        "CONTINUE_INDEPENDENT_BRANCHES",
        now,
        null,
        null,
        0L,
        Map.of(),
        1,
        0,
        List.of());
  }
}
