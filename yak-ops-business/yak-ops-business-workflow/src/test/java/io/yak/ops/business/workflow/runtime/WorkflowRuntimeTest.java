package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.observability.WorkflowEventStream;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO.EdgeDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeTest {

  private static final Set<String> TERMINAL = Set.of(
      "SUCCESS", "SUCCESS_WITH_WARNINGS", "FAILED", "CANCELED", "TIMED_OUT");

  private WorkflowRuntime service;

  @AfterEach
  void tearDown() {
    if (service != null) {
      service.shutdown();
    }
  }

  @Test
  void shouldExecuteReferencedSyncTasksInSerial() throws InterruptedException {
    FakeTaskExecutor executor = new FakeTaskExecutor();
    service = service(executor, "task-a", "task-b");
    WorkflowRunDTO request = new WorkflowRunDTO(
        "serial",
        List.of(node("a", "task-a"), node("b", "task-b")),
        List.of(new EdgeDTO("a", "b")),
        Map.of());

    WorkflowInstanceVO started = service.run(request);
    assertThat(node(started, "a").taskId()).isEqualTo("task-a");
    assertThat(node(started, "b").status()).isEqualTo("WAITING");
    service.activate(started.id());

    WorkflowInstanceVO completed = waitForTerminal(started.id());
    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(node(completed, "a").output()).containsEntry("taskId", "task-a");
    assertThat(node(completed, "b").status()).isEqualTo("SUCCESS");
  }

  @Test
  void shouldRetryRealTaskUsingNewWorkflowAttempt() throws InterruptedException {
    FakeTaskExecutor executor = new FakeTaskExecutor();
    executor.failNext("task-a", 1);
    service = service(executor, "task-a");
    WorkflowRunDTO request = new WorkflowRunDTO(
        "retry",
        List.of(node("a", "task-a")),
        List.of(),
        Map.of());

    WorkflowInstanceVO started = service.run(request);
    service.activate(started.id());
    WorkflowInstanceVO failed = waitForTerminal(started.id());
    String firstAttempt = node(failed, "a").currentAttemptId();

    service.retryFailedNode(started.id(), "a");
    WorkflowInstanceVO completed = waitForTerminal(started.id());
    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(node(completed, "a").attemptCount()).isEqualTo(2);
    assertThat(node(completed, "a").currentAttemptId()).isNotEqualTo(firstAttempt);
    assertThat(executor.started("task-a")).isEqualTo(2);
  }

  @Test
  void shouldPreserveFailurePropagation() throws InterruptedException {
    FakeTaskExecutor executor = new FakeTaskExecutor();
    executor.failNext("task-bad", 10);
    service = service(executor, "task-root", "task-bad", "task-blocked", "task-independent");
    WorkflowRunDTO request = new WorkflowRunDTO(
        "failure",
        List.of(
            node("root", "task-root"),
            node("bad", "task-bad"),
            node("blocked", "task-blocked"),
            node("independent", "task-independent")),
        List.of(
            new EdgeDTO("root", "bad"),
            new EdgeDTO("bad", "blocked"),
            new EdgeDTO("root", "independent")),
        Map.of());

    WorkflowInstanceVO started = service.run(request);
    service.activate(started.id());
    WorkflowInstanceVO completed = waitForTerminal(started.id());

    assertThat(completed.status()).isEqualTo("FAILED");
    assertThat(node(completed, "bad").status()).isEqualTo("FAILED");
    assertThat(node(completed, "blocked").status()).isEqualTo("UPSTREAM_FAILED");
    assertThat(node(completed, "independent").status()).isEqualTo("SUCCESS");
  }

  @Test
  void shouldKeepInputMappingAcrossTaskReferences() throws InterruptedException {
    FakeTaskExecutor executor = new FakeTaskExecutor();
    service = service(executor, "task-load", "task-consume");
    NodeDTO load = node(
        "load", "task-load", Map.of("requestId", "$workflow.requestId"));
    NodeDTO consume = node(
        "consume", "task-consume", Map.of("requestId", "load.receivedInput.requestId"));

    WorkflowInstanceVO started = service.run(new WorkflowRunDTO(
        "mapping",
        List.of(load, consume),
        List.of(new EdgeDTO("load", "consume")),
        Map.of("requestId", "REQ-001")));
    service.activate(started.id());
    WorkflowInstanceVO completed = waitForTerminal(started.id());

    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(node(completed, "load").input()).containsEntry("requestId", "REQ-001");
    assertThat(node(completed, "consume").input()).containsEntry("requestId", "REQ-001");
  }

  @Test
  void shouldCancelSyncExecutionWhenWorkflowTimesOut() throws InterruptedException {
    FakeTaskExecutor executor = new FakeTaskExecutor();
    executor.duration("task-slow", 3_000L);
    service = service(executor, "task-slow");
    WorkflowInstanceVO started = service.run(new WorkflowRunDTO(
        "timeout",
        List.of(node("slow", "task-slow")),
        List.of(),
        Map.of(),
        1L,
        "CONTINUE_INDEPENDENT_BRANCHES"));
    service.activate(started.id());

    WorkflowInstanceVO completed = waitForTerminal(started.id());
    assertThat(completed.status()).isEqualTo("TIMED_OUT");
    assertThat(node(completed, "slow").status()).isEqualTo("CANCELED");
    waitForCancel(executor);
    assertThat(executor.cancelCount()).isGreaterThanOrEqualTo(1);
  }

  private NodeDTO node(String id, String taskId) {
    return node(id, taskId, Map.of());
  }

  private NodeDTO node(String id, String taskId, Map<String, String> inputMapping) {
    return new NodeDTO(
        id, taskId, 1, 0L, 0L, 0L,
        inputMapping, "ALL_SUCCESS", "FAIL_WORKFLOW");
  }

  private WorkflowRuntime service(FakeTaskExecutor executor, String... taskIds) {
    Map<String, TaskDefinition> tasks = new ConcurrentHashMap<>();
    for (String taskId : taskIds) {
      tasks.put(taskId, new TaskDefinition(taskId, taskId, "SYNC"));
    }
    TaskRegistry registry = new TaskRegistry() {
      @Override
      public List<TaskDefinition> list() {
        return List.copyOf(tasks.values());
      }

      @Override
      public TaskDefinition get(String taskId) {
        TaskDefinition task = tasks.get(taskId);
        if (task == null) {
          throw new IllegalArgumentException("任务不存在：" + taskId);
        }
        return task;
      }
    };
    return new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        new TaskExecutionGateway(List.of(executor)),
        2L);
  }

  private WorkflowInstanceVO waitForTerminal(String executionId) throws InterruptedException {
    for (int i = 0; i < 800; i++) {
      WorkflowInstanceVO current = service.getInstance(executionId);
      if (TERMINAL.contains(current.status())) {
        return current;
      }
      Thread.sleep(10L);
    }
    return service.getInstance(executionId);
  }

  private void waitForCancel(FakeTaskExecutor executor) throws InterruptedException {
    for (int i = 0; i < 100 && executor.cancelCount() == 0; i++) {
      Thread.sleep(5L);
    }
  }

  private WorkflowInstanceVO.NodeInstanceVO node(WorkflowInstanceVO instance, String id) {
    return instance.nodes().stream().filter(item -> id.equals(item.id())).findFirst().orElseThrow();
  }

  private static final class FakeTaskExecutor implements TaskExecutor {
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger cancels = new AtomicInteger();
    private final ConcurrentMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> starts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> durations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, State> executions = new ConcurrentHashMap<>();

    void failNext(String taskId, int count) {
      failures.put(taskId, new AtomicInteger(count));
    }

    void duration(String taskId, long millis) {
      durations.put(taskId, millis);
    }

    int started(String taskId) {
      AtomicInteger value = starts.get(taskId);
      return value == null ? 0 : value.get();
    }

    int cancelCount() {
      return cancels.get();
    }

    @Override
    public String taskType() {
      return "SYNC";
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        String idempotencyKey,
        Map<String, Object> input) {
      String taskId = snapshot.taskId();
      AtomicInteger remaining = failures.computeIfAbsent(taskId, ignored -> new AtomicInteger());
      boolean fail = remaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
      String id = String.valueOf(sequence.incrementAndGet());
      State state = new State(
          id,
          taskId,
          System.nanoTime(),
          durations.getOrDefault(taskId, 20L),
          fail ? "FAILED" : "SUCCEEDED");
      executions.put(id, state);
      starts.computeIfAbsent(taskId, ignored -> new AtomicInteger()).incrementAndGet();
      return view(state);
    }

    @Override
    public TaskExecution status(String executionId) {
      return view(executions.get(executionId));
    }

    @Override
    public void cancel(String executionId) {
      State state = executions.get(executionId);
      if (state != null) {
        state.canceled = true;
        cancels.incrementAndGet();
      }
    }

    private TaskExecution view(State state) {
      if (state == null) {
        throw new IllegalArgumentException("execution not found");
      }
      long elapsed = (System.nanoTime() - state.startedNanos) / 1_000_000L;
      String status = state.canceled
          ? "CANCELED"
          : elapsed >= state.durationMillis ? state.terminalStatus : "RUNNING";
      return new TaskExecution(
          state.executionId,
          status,
          "FAILED".equals(status) ? "planned failure" : null,
          Map.of("taskId", state.taskId));
    }

    private static final class State {
      private final String executionId;
      private final String taskId;
      private final long startedNanos;
      private final long durationMillis;
      private final String terminalStatus;
      private volatile boolean canceled;

      private State(
          String executionId,
          String taskId,
          long startedNanos,
          long durationMillis,
          String terminalStatus) {
        this.executionId = executionId;
        this.taskId = taskId;
        this.startedNanos = startedNanos;
        this.durationMillis = durationMillis;
        this.terminalStatus = terminalStatus;
      }
    }
  }
}
