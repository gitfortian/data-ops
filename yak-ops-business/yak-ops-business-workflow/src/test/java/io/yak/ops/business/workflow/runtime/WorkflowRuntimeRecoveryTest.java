package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.observability.WorkflowEventStream;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.workflow.engine.support.InMemoryExecutionRepository;
import io.yak.framework.workflow.engine.support.InMemoryWorkflowDefinitionRepository;
import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.repository.InMemoryWorkflowRuntimeRepository;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeRecoveryTest {

  private WorkflowRuntime first;
  private WorkflowRuntime second;

  @AfterEach
  void tearDown() {
    if (first != null) first.shutdown();
    if (second != null) second.shutdown();
  }

  @Test
  void shouldRestartPersistedSubmittedAttemptWithSameAttemptId()
      throws InterruptedException {
    InMemoryWorkflowDefinitionRepository definitions = new InMemoryWorkflowDefinitionRepository();
    InMemoryExecutionRepository executions = new InMemoryExecutionRepository();
    InMemoryWorkflowRuntimeRepository runtime = new InMemoryWorkflowRuntimeRepository();
    RecordingTaskExecutor executor = new RecordingTaskExecutor();
    TaskRegistry registry = registry();

    first = service(registry, executor, definitions, executions, runtime);
    WorkflowInstanceVO prepared = first.run(request());
    String attemptId = node(prepared).currentAttemptId();
    assertThat(executor.starts()).isZero();
    first.shutdown();
    first = null;

    second = service(registry, executor, definitions, executions, runtime);
    second.activate(prepared.id());
    assertThat(second.recoverPersistedExecutions()).isEqualTo(1);
    waitFor(() -> executor.starts() == 1);

    WorkflowInstanceVO recovered = second.getInstance(prepared.id());
    assertThat(node(recovered).currentAttemptId()).isEqualTo(attemptId);
    assertThat(node(recovered).attemptCount()).isEqualTo(1);
    assertThat(executor.idempotencyKey()).isEqualTo(attemptId);
  }

  @Test
  void shouldPollBoundRemoteExecutionWithoutStartingAnotherOne()
      throws InterruptedException {
    InMemoryWorkflowDefinitionRepository definitions = new InMemoryWorkflowDefinitionRepository();
    InMemoryExecutionRepository executions = new InMemoryExecutionRepository();
    InMemoryWorkflowRuntimeRepository runtime = new InMemoryWorkflowRuntimeRepository();
    RecordingTaskExecutor executor = new RecordingTaskExecutor();
    TaskRegistry registry = registry();

    first = service(registry, executor, definitions, executions, runtime);
    WorkflowInstanceVO prepared = first.run(request());
    first.activate(prepared.id());
    waitFor(() -> executor.starts() == 1);
    waitFor(() -> "RUNNING".equals(node(first.getInstance(prepared.id())).status()));
    String attemptId = node(first.getInstance(prepared.id())).currentAttemptId();
    first.shutdown();
    first = null;

    second = service(registry, executor, definitions, executions, runtime);
    second.activate(prepared.id());
    assertThat(second.recoverPersistedExecutions()).isEqualTo(1);
    waitFor(() -> executor.statusCalls() > 0);

    assertThat(executor.starts()).isEqualTo(1);
    assertThat(node(second.getInstance(prepared.id())).currentAttemptId()).isEqualTo(attemptId);
  }

  private WorkflowRuntime service(
      TaskRegistry registry,
      RecordingTaskExecutor executor,
      InMemoryWorkflowDefinitionRepository definitions,
      InMemoryExecutionRepository executions,
      InMemoryWorkflowRuntimeRepository runtime) {
    return new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        new TaskExecutionGateway(List.of(executor)),
        10_000L,
        definitions,
        executions,
        runtime);
  }

  private TaskRegistry registry() {
    return new TaskRegistry() {
      @Override
      public List<TaskDefinition> list() {
        return List.of(new TaskDefinition("task-a", "Task A", "SYNC"));
      }

      @Override
      public TaskDefinition get(String taskId) {
        return new TaskDefinition(taskId, "Task A", "SYNC");
      }
    };
  }

  private WorkflowRunSpec request() {
    return new WorkflowRunSpec(
        "recovery",
        List.of(new WorkflowNodeSpec(
            "a", "task-a", 0D, 0D, 1, 0L, 0L, 0L,
            Map.of(), "ALL_SUCCESS", "FAIL_WORKFLOW")),
        List.of(),
        Map.of(),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES");
  }

  private WorkflowInstanceVO.NodeInstanceVO node(WorkflowInstanceVO instance) {
    return instance.nodes().stream()
        .filter(node -> "a".equals(node.id()))
        .findFirst()
        .orElseThrow();
  }

  private void waitFor(Check check) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      if (check.done()) return;
      Thread.sleep(10L);
    }
    assertThat(check.done()).isTrue();
  }

  @FunctionalInterface
  private interface Check {
    boolean done();
  }

  private static final class RecordingTaskExecutor implements TaskExecutor {
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger statusCalls = new AtomicInteger();
    private final ConcurrentMap<String, TaskExecution> executions = new ConcurrentHashMap<>();
    private volatile String idempotencyKey;

    int starts() {
      return starts.get();
    }

    int statusCalls() {
      return statusCalls.get();
    }

    String idempotencyKey() {
      return idempotencyKey;
    }

    @Override
    public String taskType() {
      return "SYNC";
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        String key,
        Map<String, Object> input) {
      idempotencyKey = key;
      int number = starts.incrementAndGet();
      String id = "remote-" + number;
      TaskExecution execution = new TaskExecution(
          id,
          "RUNNING",
          null,
          Map.of("idempotencyKey", key == null ? "" : key));
      executions.put(id, execution);
      return execution;
    }

    @Override
    public TaskExecution status(String executionId) {
      statusCalls.incrementAndGet();
      TaskExecution execution = executions.get(executionId);
      if (execution == null) {
        throw new IllegalArgumentException("execution not found: " + executionId);
      }
      return execution;
    }

    @Override
    public void cancel(String executionId) {
      // Not used by these recovery tests.
    }
  }
}
