package io.yak.ops.business.development.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSubmission;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DevelopmentTaskExecutionControlServiceTest {

  @Test
  void refreshesTerminalRuntimeStateIntoDurableHistory() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    ProjectContextScope projectScope = mock(ProjectContextScope.class);
    DevelopmentTaskExecutionDetail running = execution("RUNNING", "runtime-1", null, null);
    DevelopmentTaskExecutionDetail completed = execution("SUCCESS", "runtime-1", null, null);
    when(histories.get(10L)).thenReturn(running, completed);
    when(gateway.status("SQL", "runtime-1"))
        .thenReturn(new TaskExecution("runtime-1", "SUCCEEDED", null, Map.of("rows", 1)));

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway, projectScope);

    assertEquals("SUCCESS", service.refresh(10L).status());
    verify(histories).complete(
        eq(10L), eq("SUCCESS"), anyLong(), eq(null), eq(null), eq(Map.of("rows", 1)));
  }

  @Test
  void marksLostRuntimeStateWithStructuredReason() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    ProjectContextScope projectScope = mock(ProjectContextScope.class);
    DevelopmentTaskExecutionDetail running = execution("RUNNING", "runtime-lost", null, null);
    DevelopmentTaskExecutionDetail failed = execution(
        "FAILED",
        "runtime-lost",
        null,
        DevelopmentTaskExecutionService.FAILURE_RUNTIME_STATE_LOST);
    when(histories.get(10L)).thenReturn(running, failed);
    when(gateway.status("SQL", "runtime-lost"))
        .thenThrow(new IllegalArgumentException("runtime not found"));

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway, projectScope);

    DevelopmentTaskExecutionDetail result = service.refresh(10L);

    assertEquals(DevelopmentTaskExecutionService.FAILURE_RUNTIME_STATE_LOST, result.failureReason());
    verify(histories).complete(
        eq(10L),
        eq("FAILED"),
        anyLong(),
        eq(DevelopmentTaskExecutionService.FAILURE_RUNTIME_STATE_LOST),
        eq("运行时状态不可用，任务可能因服务重启而中断"),
        eq(Map.of()));
  }

  @Test
  void marksStaleUnattachedExecutionWithStructuredReason() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    ProjectContextScope projectScope = mock(ProjectContextScope.class);
    DevelopmentTaskExecutionDetail stale = executionAt(
        "PENDING",
        null,
        null,
        null,
        LocalDateTime.now().minusMinutes(1));
    DevelopmentTaskExecutionDetail failed = execution(
        "FAILED",
        null,
        null,
        DevelopmentTaskExecutionService.FAILURE_RUNTIME_NOT_ATTACHED);
    when(histories.get(10L)).thenReturn(stale, failed);

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway, projectScope);

    DevelopmentTaskExecutionDetail result = service.refresh(10L);

    assertEquals(DevelopmentTaskExecutionService.FAILURE_RUNTIME_NOT_ATTACHED, result.failureReason());
    verify(histories).complete(
        eq(10L),
        eq("FAILED"),
        anyLong(),
        eq(DevelopmentTaskExecutionService.FAILURE_RUNTIME_NOT_ATTACHED),
        eq("任务已创建但运行时未完成接管，可能在提交过程中发生服务重启"),
        eq(Map.of()));
  }

  @Test
  void cancelConvergesTheRuntimeIntoCancelledDurableState() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    ProjectContextScope projectScope = mock(ProjectContextScope.class);
    DevelopmentTaskExecutionDetail running = execution("RUNNING", "runtime-1", null, null);
    DevelopmentTaskExecutionDetail cancelled = execution("CANCELLED", "runtime-1", null, null);
    when(histories.get(10L)).thenReturn(running, running, running, cancelled);
    when(gateway.status("SQL", "runtime-1"))
        .thenReturn(
            new TaskExecution("runtime-1", "RUNNING", null, Map.of()),
            new TaskExecution("runtime-1", "CANCELED", null, Map.of("cancelled", true)));

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway, projectScope);

    DevelopmentTaskExecutionDetail result = service.cancel(10L);

    assertEquals("CANCELLED", result.status());
    verify(gateway).cancel("SQL", "runtime-1");
    verify(histories).complete(
        eq(10L),
        eq("CANCELLED"),
        anyLong(),
        eq(null),
        eq(null),
        eq(Map.of("cancelled", true)));
  }

  @Test
  void retriesTheExactPersistedDefinitionAndLinksThePreviousExecution() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    ProjectContextScope projectScope = mock(ProjectContextScope.class);
    DevelopmentTaskExecutionDetail failed = execution("FAILED", "runtime-1", null, "TASK_FAILED");
    DevelopmentTaskExecutionSubmission retried = new DevelopmentTaskExecutionSubmission(
        11L, 7L, "SQL", "runtime-2", TaskExecutionStatus.RUNNING);
    when(histories.get(10L)).thenReturn(failed);
    when(runs.submit(7L, "SQL", 3, "select 1", "{\"dataSourceId\":\"9\"}", "bruce", 10L))
        .thenReturn(retried);

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway, projectScope);

    DevelopmentTaskExecutionSubmission result = service.retry(10L, "bruce");

    assertEquals(11L, result.id());
    verify(runs).submit(7L, "SQL", 3, "select 1", "{\"dataSourceId\":\"9\"}", "bruce", 10L);
  }

  @Test
  void backgroundReconciliationRestoresPersistedProjectContext() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    AtomicReference<ProjectContext> restored = new AtomicReference<>();
    ProjectContextScope projectScope = new ProjectContextScope() {
      @Override
      public <T> T call(ProjectContext context, Supplier<T> action) {
        restored.set(context);
        return action.get();
      }
    };

    DevelopmentTaskExecutionDetail running = execution("RUNNING", "runtime-1", null, null);
    DevelopmentTaskExecutionDetail completed = execution("SUCCESS", "runtime-1", null, null);
    when(histories.listActiveForReconciliation(10)).thenReturn(List.of(
        new DevelopmentTaskExecutionService.ReconciliationCandidate(44L, running)));
    when(gateway.status("SQL", "runtime-1"))
        .thenReturn(new TaskExecution("runtime-1", "SUCCEEDED", null, Map.of("rows", 1)));
    when(histories.get(10L)).thenReturn(completed);

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway, projectScope);

    service.reconcileActiveExecutions(10);

    assertEquals(44L, restored.get().projectId());
    verify(histories).complete(
        eq(10L), eq("SUCCESS"), anyLong(), eq(null), eq(null), eq(Map.of("rows", 1)));
  }

  private DevelopmentTaskExecutionDetail execution(
      String status,
      String runtimeExecutionId,
      Long retryOfExecutionId,
      String failureReason) {
    return executionAt(
        status,
        runtimeExecutionId,
        retryOfExecutionId,
        failureReason,
        LocalDateTime.now().minusSeconds(1));
  }

  private DevelopmentTaskExecutionDetail executionAt(
      String status,
      String runtimeExecutionId,
      Long retryOfExecutionId,
      String failureReason,
      LocalDateTime startTime) {
    return new DevelopmentTaskExecutionDetail(
        10L,
        7L,
        "测试任务",
        "SQL",
        3,
        "MANUAL",
        runtimeExecutionId,
        retryOfExecutionId,
        status,
        "bruce",
        "RUNNING".equals(status) || "PENDING".equals(status) ? null : 100L,
        failureReason,
        null,
        "select 1",
        "{\"dataSourceId\":\"9\"}",
        Map.of(),
        startTime,
        "RUNNING".equals(status) || "PENDING".equals(status) ? null : LocalDateTime.now());
  }
}
