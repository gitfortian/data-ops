package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.observability.WorkflowEventStream;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeVersionSnapshotTest {

  private WorkflowRuntime service;

  @AfterEach
  void tearDown() {
    if (service != null) service.shutdown();
  }

  @Test
  void shouldExecutePinnedSnapshotWithoutReadingCurrentTaskRegistry() throws InterruptedException {
    TaskRegistry registry = new TaskRegistry() {
      @Override public List<TaskDefinition> list() { return List.of(); }
      @Override public TaskDefinition get(String taskId) {
        throw new AssertionError("published runtime must not read current TaskRegistry");
      }
    };
    SnapshotTaskExecutor executor = new SnapshotTaskExecutor();
    service = new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        new TaskExecutionGateway(List.of(executor)),
        2L);
    TaskVersionSnapshot pinned = new TaskVersionSnapshot(
        "task-a", "订单同步", "SYNC", 17L, "digest-17", "{\"definition\":17}", "{\"jobSpec\":17}");
    WorkflowRunSpec request = request("published-v3");

    WorkflowInstanceVO prepared = service.run(request, Map.of("a", pinned), "version-3", 3, false);
    service.activate(prepared.id());
    WorkflowInstanceVO completed = waitForTerminal(prepared.id());

    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(completed.workflowVersionId()).isEqualTo("version-3");
    assertThat(completed.workflowVersionNo()).isEqualTo(3);
    assertThat(completed.testRun()).isFalse();
    assertThat(executor.snapshot.get()).isEqualTo(pinned);
    assertThat(completed.nodes().get(0).output()).containsEntry("taskVersion", 17L);
  }

  @Test
  void shouldPollRemoteStatusAsScheduledShortOperations() throws InterruptedException {
    TaskRegistry registry = new TaskRegistry() {
      @Override public List<TaskDefinition> list() { return List.of(); }
      @Override public TaskDefinition get(String taskId) { return new TaskDefinition(taskId, taskId, "SYNC"); }
    };
    SnapshotTaskExecutor executor = new SnapshotTaskExecutor();
    executor.runningPolls.set(4);
    service = new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        new TaskExecutionGateway(List.of(executor)),
        2L);
    TaskVersionSnapshot pinned = new TaskVersionSnapshot("task-a", "A", "SYNC", 2L, null, "{}", "{}");
    WorkflowRunSpec request = request("polling");

    WorkflowInstanceVO prepared = service.run(request, Map.of("a", pinned), "version-1", 1, false);
    service.activate(prepared.id());
    WorkflowInstanceVO completed = waitForTerminal(prepared.id());

    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(executor.statusCalls.get()).isGreaterThanOrEqualTo(5);
  }

  private WorkflowRunSpec request(String name) {
    return new WorkflowRunSpec(
        name,
        List.of(new WorkflowNodeSpec(
            "a", "task-a", 0D, 0D, 1, 0L, 0L, 0L,
            Map.of(), "ALL_SUCCESS", "FAIL_WORKFLOW")),
        List.of(),
        Map.of(),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES");
  }

  private WorkflowInstanceVO waitForTerminal(String executionId) throws InterruptedException {
    for (int i = 0; i < 500; i++) {
      WorkflowInstanceVO current = service.getInstance(executionId);
      if (List.of("SUCCESS", "FAILED", "CANCELED", "TIMED_OUT").contains(current.status())) return current;
      Thread.sleep(5L);
    }
    return service.getInstance(executionId);
  }

  private static final class SnapshotTaskExecutor implements TaskExecutor {
    private final AtomicReference<TaskVersionSnapshot> snapshot = new AtomicReference<>();
    private final AtomicInteger statusCalls = new AtomicInteger();
    private final AtomicInteger runningPolls = new AtomicInteger();

    @Override
    public String taskType() {
      return "SYNC";
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot value,
        String idempotencyKey,
        Map<String, Object> input) {
      snapshot.set(value);
      return new TaskExecution("sync-1", "RUNNING", null, Map.of());
    }

    @Override
    public TaskExecution status(String executionId) {
      int call = statusCalls.incrementAndGet();
      String status = call <= runningPolls.get() ? "RUNNING" : "SUCCEEDED";
      return new TaskExecution(executionId, status, null, Map.of("calls", call));
    }

    @Override
    public void cancel(String executionId) {}
  }
}
