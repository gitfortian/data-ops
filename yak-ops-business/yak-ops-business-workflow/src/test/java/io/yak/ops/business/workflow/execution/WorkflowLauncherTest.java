package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.domain.WorkflowTriggerType;
import io.yak.ops.business.workflow.repository.WorkflowExecutionTriggerRecorder;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowLauncherTest {

  @Mock private WorkflowDefinitionManager definitionService;
  @Mock private WorkflowRuntime runtimeService;
  @Mock private WorkflowExecutionTriggerRecorder triggerRecorder;
  @Mock private WorkflowPublishedVersionRunner publishedVersionRunner;

  private WorkflowLauncher service;

  @BeforeEach
  void setUp() {
    service = new WorkflowLauncher(
        definitionService, runtimeService, triggerRecorder, publishedVersionRunner);
  }

  @Test
  void shouldRunPublishedDefinitionAndRecordTriggerContext() {
    WorkflowTriggerContext trigger = new WorkflowTriggerContext(
        WorkflowTriggerType.SCHEDULE,
        "schedule-trigger-20260814T020000",
        "schedule-1",
        Instant.parse("2026-08-14T02:00:00Z"));
    WorkflowDefinitionVO expected = definition("execution-1", "workflow-version-1", 1);
    when(definitionService.run("workflow-1")).thenReturn(expected);

    WorkflowDefinitionVO actual = service.runPublished("workflow-1", trigger);

    assertThat(actual).isSameAs(expected);
    verify(definitionService).run("workflow-1");
    verify(triggerRecorder).record("execution-1", trigger);
  }

  @Test
  void shouldUseConcurrentDefinitionEntryForScheduledAdmission() {
    WorkflowTriggerContext trigger = WorkflowTriggerContext.scheduled(
        "trigger-parallel",
        "schedule-1",
        Instant.parse("2026-08-14T02:00:00Z"));
    WorkflowDefinitionVO expected = definition("execution-parallel", "workflow-version-1", 1);
    when(definitionService.runConcurrent("workflow-1")).thenReturn(expected);

    WorkflowDefinitionVO actual = service.runScheduledPublished("workflow-1", trigger);

    assertThat(actual).isSameAs(expected);
    verify(definitionService).runConcurrent("workflow-1");
    verify(triggerRecorder).record("execution-parallel", trigger);
  }

  @Test
  void shouldKeepBackfillPinnedToRequestedVersionAfterActiveVersionChanges() {
    WorkflowTriggerContext trigger = WorkflowTriggerContext.backfill(
        "backfill-trigger-1",
        "schedule-1",
        "backfill-1",
        Instant.parse("2026-08-14T02:00:00Z"),
        "Asia/Shanghai");
    WorkflowDefinitionVO current = definition("latest-normal", "workflow-version-6", 6);
    WorkflowInstanceVO expected = instance("execution-v5", "RUNNING");
    when(definitionService.get("workflow-1")).thenReturn(current);
    when(publishedVersionRunner.run("workflow-1", "workflow-version-5")).thenReturn(expected);

    WorkflowInstanceVO actual = service.runBackfillPublished(
        "workflow-1",
        "workflow-version-5",
        trigger,
        Map.of("businessDate", "2026-08-14"));

    assertThat(actual).isSameAs(expected);
    verify(publishedVersionRunner).run("workflow-1", "workflow-version-5");
    verify(triggerRecorder).record("execution-v5", trigger);
  }

  @Test
  void shouldRecordAdHocTriggerBeforeActivatingPreparedExecution() {
    WorkflowRunDTO request = new WorkflowRunDTO(
        "临时工作流",
        List.of(new NodeDTO("node-1", "task-1")),
        List.of(),
        Map.of());
    WorkflowTriggerContext trigger = WorkflowTriggerContext.api();
    WorkflowInstanceVO prepared = instance("execution-api", "RUNNING");
    WorkflowInstanceVO activated = instance("execution-api", "RUNNING");
    when(runtimeService.run(request)).thenReturn(prepared);
    when(runtimeService.activate("execution-api")).thenReturn(activated);

    WorkflowInstanceVO actual = service.runAdHoc(request, trigger);

    assertThat(actual).isSameAs(activated);
    InOrder order = inOrder(runtimeService, triggerRecorder);
    order.verify(runtimeService).run(request);
    order.verify(triggerRecorder).record("execution-api", trigger);
    order.verify(runtimeService).activate("execution-api");
  }

  @Test
  void shouldActivateRestartBeforeReturningRunningInstance() {
    WorkflowTriggerContext trigger = WorkflowTriggerContext.manual();
    WorkflowInstanceVO prepared = instance("execution-restart", "RUNNING");
    WorkflowInstanceVO activated = instance("execution-restart", "RUNNING");
    when(runtimeService.restart("execution-source")).thenReturn(prepared);
    when(runtimeService.activate("execution-restart")).thenReturn(activated);

    WorkflowInstanceVO actual = service.restart("execution-source", trigger);

    assertThat(actual).isSameAs(activated);
    InOrder order = inOrder(runtimeService, triggerRecorder);
    order.verify(runtimeService).restart("execution-source");
    order.verify(triggerRecorder).record("execution-restart", trigger);
    order.verify(runtimeService).activate("execution-restart");
  }

  @Test
  void shouldActivateNodeRerunBeforeReturningRunningInstance() {
    WorkflowTriggerContext trigger = WorkflowTriggerContext.manual();
    WorkflowInstanceVO prepared = instance("execution-rerun", "RUNNING");
    WorkflowInstanceVO activated = instance("execution-rerun", "RUNNING");
    when(runtimeService.rerunFromNode("execution-source", "node-2")).thenReturn(prepared);
    when(runtimeService.activate("execution-rerun")).thenReturn(activated);

    WorkflowInstanceVO actual = service.rerunFromNode("execution-source", "node-2", trigger);

    assertThat(actual).isSameAs(activated);
    InOrder order = inOrder(runtimeService, triggerRecorder);
    order.verify(runtimeService).rerunFromNode("execution-source", "node-2");
    order.verify(triggerRecorder).record("execution-rerun", trigger);
    order.verify(runtimeService).activate("execution-rerun");
  }

  private WorkflowDefinitionVO definition(
      String executionId,
      String activeVersionId,
      int activeVersionNo) {
    Instant now = Instant.parse("2026-08-13T12:00:00Z");
    return new WorkflowDefinitionVO(
        "workflow-1",
        "订单同步",
        "",
        "ONLINE",
        0,
        0,
        List.of(),
        List.of(),
        Map.of(),
        Map.of(),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES",
        activeVersionId,
        activeVersionNo,
        activeVersionNo,
        false,
        executionId,
        "RUNNING",
        now,
        now);
  }

  private WorkflowInstanceVO instance(String executionId, String status) {
    Instant now = Instant.parse("2026-08-13T12:00:00Z");
    return new WorkflowInstanceVO(
        executionId,
        "runtime-definition",
        null,
        "临时工作流",
        status,
        "CONTINUE_INDEPENDENT_BRANCHES",
        now,
        null,
        null,
        0L,
        Map.of(),
        0,
        0,
        List.of());
  }
}
