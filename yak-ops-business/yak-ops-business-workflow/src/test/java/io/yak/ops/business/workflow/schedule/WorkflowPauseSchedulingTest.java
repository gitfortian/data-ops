package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.observability.WorkflowEventStream;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

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

class WorkflowPauseSchedulingTest {

  private static final Set<String> TERMINAL = Set.of(
      "SUCCESS", "SUCCESS_WITH_WARNINGS", "FAILED", "CANCELED", "TIMED_OUT");

  private WorkflowRuntime service;

  @AfterEach
  void tearDown() throws ReflectiveOperationException {
    if (service != null) {
      var shutdown = WorkflowRuntime.class.getDeclaredMethod("shutdown");
      shutdown.setAccessible(true);
      shutdown.invoke(service);
    }
  }

  @Test
  void shouldLetRunningSyncTaskFinishBeforeWorkflowBecomesPaused() throws InterruptedException {
    FakeRunner runner = new FakeRunner();
    runner.duration("task-a", 300L);
    runner.duration("task-b", 20L);
    service = service(runner, "task-a", "task-b");

    WorkflowInstanceVO prepared = service.run(new WorkflowRunDTO(
        "pause-scheduling",
        List.of(node("a", "task-a"), node("b", "task-b")),
        List.of(new EdgeDTO("a", "b")),
        Map.of()));
    service.activate(prepared.id());
    waitUntilStarted(runner, "task-a");

    WorkflowInstanceVO pauseRequested = service.pause(prepared.id());
    assertThat(pauseRequested.status()).isEqualTo("PAUSING");

    Thread.sleep(40L);
    WorkflowInstanceVO stillPausing = service.getInstance(prepared.id());
    assertThat(stillPausing.status()).isEqualTo("PAUSING");
    assertThat(node(stillPausing, "a").status()).isEqualTo("RUNNING");
    assertThat(runner.started("task-b")).isZero();
    assertThat(runner.cancelCount()).isZero();

    WorkflowInstanceVO paused = waitForStatus(prepared.id(), "PAUSED");
    assertThat(node(paused, "a").status()).isEqualTo("SUCCESS");
    assertThat(runner.started("task-b")).isZero();

    service.resume(prepared.id());
    WorkflowInstanceVO completed = waitForTerminal(prepared.id());
    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(node(completed, "b").status()).isEqualTo("SUCCESS");
    assertThat(runner.started("task-b")).isEqualTo(1);
  }

  private NodeDTO node(String id, String taskId) {
    return new NodeDTO(
        id, taskId, 1, 0L, 0L, 0L, Map.of(), "ALL_SUCCESS", "FAIL_WORKFLOW");
  }

  private WorkflowRuntime service(FakeRunner runner, String... taskIds) {
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
        new WorkflowEventStream(), registry, new TaskExecutionGateway(List.of(runner)), 2L);
  }

  private void waitUntilStarted(FakeRunner runner, String taskId) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      if (runner.started(taskId) > 0) {
        return;
      }
      Thread.sleep(5L);
    }
    assertThat(runner.started(taskId)).isGreaterThan(0);
  }

  private WorkflowInstanceVO waitForStatus(String executionId, String expected)
      throws InterruptedException {
    for (int i = 0; i < 400; i++) {
      WorkflowInstanceVO current = service.getInstance(executionId);
      if (expected.equals(current.status())) {
        return current;
      }
      Thread.sleep(5L);
    }
    return service.getInstance(executionId);
  }

  private WorkflowInstanceVO waitForTerminal(String executionId) throws InterruptedException {
    for (int i = 0; i < 400; i++) {
      WorkflowInstanceVO current = service.getInstance(executionId);
      if (TERMINAL.contains(current.status())) {
        return current;
      }
      Thread.sleep(5L);
    }
    return service.getInstance(executionId);
  }

  private WorkflowInstanceVO.NodeInstanceVO node(WorkflowInstanceVO instance, String id) {
    return instance.nodes().stream().filter(item -> id.equals(item.id())).findFirst().orElseThrow();
  }

  private static final class FakeRunner implements TaskExecutor {
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger cancels = new AtomicInteger();
    private final ConcurrentMap<String, AtomicInteger> starts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> durations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, State> executions = new ConcurrentHashMap<>();

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
        TaskVersionSnapshot snapshot, String idempotencyKey, Map<String, Object> input) {
      String taskId = snapshot.taskId();
      String id = String.valueOf(sequence.incrementAndGet());
      State state = new State(
          id,
          taskId,
          System.nanoTime(),
          durations.getOrDefault(taskId, 20L));
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
          : elapsed >= state.durationMillis ? "SUCCEEDED" : "RUNNING";
      return new TaskExecution(
          state.executionId,
          status,
          null,
          Map.of("taskId", state.taskId));
    }

    private static final class State {
      private final String executionId;
      private final String taskId;
      private final long startedNanos;
      private final long durationMillis;
      private volatile boolean canceled;

      private State(String executionId, String taskId, long startedNanos, long durationMillis) {
        this.executionId = executionId;
        this.taskId = taskId;
        this.startedNanos = startedNanos;
        this.durationMillis = durationMillis;
      }
    }
  }
}
