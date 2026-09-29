package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSubmission;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentTaskRunServiceTest {

  private DevelopmentTaskRunService service;
  private RecordingRuntimeExecutor runtimeExecutor;
  private DevelopmentTaskExecutionService executionService;

  @BeforeEach
  void setUp() {
    DevelopmentNodeRepository nodeRepository = mock(DevelopmentNodeRepository.class);
    Instant now = Instant.parse("2026-08-12T00:00:00Z");
    when(nodeRepository.findById(1L))
        .thenReturn(
            Optional.of(
                new DevelopmentNode(1L, "今天统计", "SQL", null, null, true, now, now)));

    runtimeExecutor = new RecordingRuntimeExecutor();
    executionService = mock(DevelopmentTaskExecutionService.class);
    when(executionService.createPending(
            any(), anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
        .thenReturn(99L);
    service =
        new DevelopmentTaskRunService(
            nodeRepository,
            new TaskExecutionGateway(List.of(runtimeExecutor)),
            executionService,
            new ObjectMapper());
  }

  @Test
  void submitsCurrentDefinitionWithoutWaitingForTerminalState() {
    DevelopmentTaskExecutionSubmission result =
        service.submit(1L, "sql", 1, "select 42", "{\"dataSourceId\":\"7\"}", "bruce");

    assertEquals(99L, result.id());
    assertEquals(TaskExecutionStatus.RUNNING, result.status());
    assertEquals("manual-1", result.runtimeExecutionId());
    assertEquals(TaskExecutionTrigger.MANUAL, runtimeExecutor.lastTrigger);
    assertEquals("development:1", runtimeExecutor.lastSnapshot.taskId());
    assertEquals(0L, runtimeExecutor.lastSnapshot.version());
    assertTrue(runtimeExecutor.lastSnapshot.definitionSnapshotJson().contains("select 42"));
    assertEquals(0, runtimeExecutor.statusCalls);
    verify(executionService).attachRuntime(99L, "manual-1", "RUNNING");
    verify(executionService, never()).complete(anyLong(), anyString(), anyLong(), any(), any());
  }

  @Test
  void persistsImmediatelyTerminalRuntimeResult() {
    runtimeExecutor.startStatus = "SUCCEEDED";

    DevelopmentTaskExecutionSubmission result =
        service.submit(1L, "SQL", 1, "select 42", "{}", "bruce");

    assertEquals(TaskExecutionStatus.SUCCESS, result.status());
    verify(executionService).attachRuntime(99L, "manual-1", "SUCCESS");
    verify(executionService).complete(eq(99L), eq("SUCCESS"), anyLong(), eq(null), any());
  }

  private static final class RecordingRuntimeExecutor implements TaskExecutor {
    private TaskExecutionTrigger lastTrigger;
    private TaskVersionSnapshot lastSnapshot;
    private String startStatus = "RUNNING";
    private int statusCalls;

    @Override
    public String taskType() {
      return "SQL";
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        String idempotencyKey,
        Map<String, Object> input) {
      return start(snapshot, TaskExecutionTrigger.WORKFLOW, idempotencyKey, input);
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        TaskExecutionTrigger trigger,
        String idempotencyKey,
        Map<String, Object> input) {
      this.lastTrigger = trigger;
      this.lastSnapshot = snapshot;
      return new TaskExecution(
          "manual-1",
          startStatus,
          null,
          Map.of("trigger", trigger.name()));
    }

    @Override
    public TaskExecution status(String executionId) {
      statusCalls += 1;
      return new TaskExecution(executionId, "SUCCEEDED", null, Map.of());
    }

    @Override
    public void cancel(String executionId) {}
  }
}
