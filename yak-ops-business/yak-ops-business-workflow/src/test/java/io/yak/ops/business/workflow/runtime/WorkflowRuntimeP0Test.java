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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeP0Test {

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
  void shouldHonorRetryDelayAndUseAttemptIdAsStartIdempotencyKey()
      throws InterruptedException {
    RecordingTaskExecutor executor = new RecordingTaskExecutor();
    executor.failNext("task-a", 1);
    service = service(executor, "task-a");

    WorkflowRunSpec request = runSpec(
        "retry-delay",
        new WorkflowNodeSpec(
            "a", "task-a", 0D, 0D, 2, 1L, 0L, 0L,
            Map.of(), "ALL_SUCCESS", "FAIL_WORKFLOW"));

    WorkflowInstanceVO started = service.run(request);
    service.activate(started.id());

    waitForStarts(executor, "task-a", 1);
    Thread.sleep(200L);
    assertThat(executor.started("task-a")).isEqualTo(1);

    WorkflowInstanceVO completed = waitForTerminal(started.id(), 3_000L);
    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(executor.started("task-a")).isEqualTo(2);
    assertThat(executor.startGapMillis("task-a")).isGreaterThanOrEqualTo(800L);

    WorkflowInstanceVO.NodeInstanceVO node = node(completed, "a");
    assertThat(executor.idempotencyKeys("task-a"))
        .hasSize(2)
        .doesNotHaveDuplicates()
        .contains(node.currentAttemptId());
  }

  @Test
  void shouldNotStartDelayedRetryAfterWorkflowWasCanceled()
      throws InterruptedException {
    RecordingTaskExecutor executor = new RecordingTaskExecutor();
    executor.failNext("task-a", 1);
    service = service(executor, "task-a");

    WorkflowRunSpec request = runSpec(
        "cancel-delayed-retry",
        new WorkflowNodeSpec(
            "a", "task-a", 0D, 0D, 2, 1L, 0L, 0L,
            Map.of(), "ALL_SUCCESS", "FAIL_WORKFLOW"));

    WorkflowInstanceVO started = service.run(request);
    service.activate(started.id());
    waitForAttemptCount(started.id(), "a", 2, 1_000L);

    WorkflowInstanceVO canceled = service.cancel(started.id());
    assertThat(canceled.status()).isEqualTo("CANCELED");

    Thread.sleep(1_200L);
    assertThat(executor.started("task-a")).isEqualTo(1);
  }

  private WorkflowRunSpec runSpec(String name, WorkflowNodeSpec node) {
    return new WorkflowRunSpec(
        name,
        List.of(node),
        List.of(),
        Map.of(),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES");
  }

  private WorkflowRuntime service(RecordingTaskExecutor executor, String... taskIds) {
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

  private WorkflowInstanceVO waitForTerminal(String executionId, long timeoutMillis)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
    while (System.nanoTime() < deadline) {
      WorkflowInstanceVO current = service.getInstance(executionId);
      if (TERMINAL.contains(current.status())) {
        return current;
      }
      Thread.sleep(10L);
    }
    return service.getInstance(executionId);
  }

  private void waitForStarts(RecordingTaskExecutor executor, String taskId, int expected)
      throws InterruptedException {
    for (int i = 0; i < 100 && executor.started(taskId) < expected; i++) {
      Thread.sleep(10L);
    }
  }

  private void waitForAttemptCount(
      String executionId,
      String nodeId,
      int expected,
      long timeoutMillis) throws InterruptedException {
    long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
    while (System.nanoTime() < deadline) {
      if (node(service.getInstance(executionId), nodeId).attemptCount() >= expected) {
        return;
      }
      Thread.sleep(10L);
    }
  }

  private WorkflowInstanceVO.NodeInstanceVO node(WorkflowInstanceVO instance, String id) {
    return instance.nodes().stream()
        .filter(item -> id.equals(item.id()))
        .findFirst()
        .orElseThrow();
  }

  private static final class RecordingTaskExecutor implements TaskExecutor {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> starts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentLinkedQueue<Long>> startTimes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentLinkedQueue<String>> idempotencyKeys = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TaskExecution> executions = new ConcurrentHashMap<>();

    void failNext(String taskId, int count) {
      failures.put(taskId, new AtomicInteger(count));
    }

    int started(String taskId) {
      AtomicInteger value = starts.get(taskId);
      return value == null ? 0 : value.get();
    }

    long startGapMillis(String taskId) {
      Long[] values = startTimes.getOrDefault(taskId, new ConcurrentLinkedQueue<>())
          .toArray(Long[]::new);
      if (values.length < 2) {
        return 0L;
      }
      return (values[1] - values[0]) / 1_000_000L;
    }

    List<String> idempotencyKeys(String taskId) {
      return List.copyOf(
          idempotencyKeys.getOrDefault(taskId, new ConcurrentLinkedQueue<>()));
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
      idempotencyKeys
          .computeIfAbsent(snapshot.taskId(), ignored -> new ConcurrentLinkedQueue<>())
          .offer(idempotencyKey);
      return startInternal(snapshot.taskId());
    }

    private TaskExecution startInternal(String taskId) {
      starts.computeIfAbsent(taskId, ignored -> new AtomicInteger()).incrementAndGet();
      startTimes.computeIfAbsent(taskId, ignored -> new ConcurrentLinkedQueue<>())
          .offer(System.nanoTime());
      AtomicInteger remaining = failures.computeIfAbsent(taskId, ignored -> new AtomicInteger());
      boolean fail = remaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
      String executionId = String.valueOf(sequence.incrementAndGet());
      TaskExecution execution = new TaskExecution(
          executionId,
          fail ? "FAILED" : "SUCCEEDED",
          fail ? "planned failure" : null,
          Map.of("taskId", taskId));
      executions.put(executionId, execution);
      return execution;
    }

    @Override
    public TaskExecution status(String executionId) {
      TaskExecution execution = executions.get(executionId);
      if (execution == null) {
        throw new IllegalArgumentException("execution not found: " + executionId);
      }
      return execution;
    }

    @Override
    public void cancel(String executionId) {
      // All test executions are terminal immediately; delayed retry cancellation is fenced before start.
    }
  }
}
