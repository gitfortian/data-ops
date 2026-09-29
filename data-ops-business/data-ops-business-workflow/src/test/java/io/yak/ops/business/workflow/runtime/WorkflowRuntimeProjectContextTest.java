package io.yak.ops.business.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.framework.workflow.engine.support.InMemoryExecutionRepository;
import io.yak.framework.workflow.engine.support.InMemoryWorkflowDefinitionRepository;
import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.observability.WorkflowEventStream;
import io.yak.ops.business.workflow.repository.InMemoryWorkflowRuntimeRepository;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeProjectContextTest {

  private static final ProjectContext PROJECT = new ProjectContext(7L, "Project A");
  private static final Set<String> TERMINAL = Set.of(
      "SUCCESS", "SUCCESS_WITH_WARNINGS", "FAILED", "CANCELED", "TIMED_OUT");

  private WorkflowRuntime runtime;

  @AfterEach
  void tearDown() {
    if (runtime != null) runtime.shutdown();
  }

  @Test
  void requiresProjectBeforeReadingTaskSnapshot() {
    TestProjectContext projectContext = new TestProjectContext();
    AtomicInteger taskLookups = new AtomicInteger();
    TaskRegistry registry = new TaskRegistry() {
      @Override
      public List<TaskDefinition> list() {
        return List.of();
      }

      @Override
      public TaskDefinition get(String taskId) {
        taskLookups.incrementAndGet();
        return new TaskDefinition(taskId, taskId, "SYNC");
      }
    };
    ProjectCheckingTaskExecutor executor = new ProjectCheckingTaskExecutor(projectContext, 20L);
    runtime = runtime(projectContext, executor, registry, 5L);

    assertThatThrownBy(() -> runtime.run(request("project-required-before-task-read", 0L)))
        .isInstanceOf(ProjectContextException.class);
    assertThat(taskLookups).hasValue(0);
  }

  @Test
  void rejectsColdRuntimeCacheFromAnotherProject() {
    TestProjectContext projectContext = new TestProjectContext();
    ProjectCheckingTaskExecutor executor = new ProjectCheckingTaskExecutor(projectContext, 5_000L);
    TaskRegistry registry = registry();
    WorkflowDefinitionRepository definitions = new InMemoryWorkflowDefinitionRepository();
    ExecutionRepository executions = new InMemoryExecutionRepository();
    ProjectAwareRuntimeRepository persistence =
        new ProjectAwareRuntimeRepository(projectContext);
    TaskExecutionGateway gateway = new TaskExecutionGateway(List.of(executor));

    WorkflowRuntime ownerRuntime = new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        gateway,
        10L,
        definitions,
        executions,
        persistence,
        projectContext,
        projectContext,
        true);
    WorkflowInstanceVO started;
    try {
      started = projectContext.call(
          PROJECT,
          () -> ownerRuntime.run(request("cold-project-boundary", 0L)));
    } finally {
      ownerRuntime.shutdown();
    }

    runtime = new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        gateway,
        10L,
        definitions,
        executions,
        persistence,
        projectContext,
        projectContext,
        true);

    assertThatThrownBy(() -> projectContext.call(
            new ProjectContext(9L, "Project B"),
            () -> runtime.getInstance(started.id())))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void rejectsCachedExecutionFromAnotherProject() {
    TestProjectContext projectContext = new TestProjectContext();
    ProjectCheckingTaskExecutor executor = new ProjectCheckingTaskExecutor(projectContext, 5_000L);
    runtime = runtime(projectContext, executor, 10L);

    WorkflowInstanceVO started = projectContext.call(
        PROJECT,
        () -> runtime.run(request("cached-project-boundary", 0L)));

    assertThatThrownBy(() -> projectContext.call(
            new ProjectContext(9L, "Project B"),
            () -> runtime.getInstance(started.id())))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void rejectsCachedExecutionWhenProjectIsMissing() {
    TestProjectContext projectContext = new TestProjectContext();
    ProjectCheckingTaskExecutor executor = new ProjectCheckingTaskExecutor(projectContext, 5_000L);
    runtime = runtime(projectContext, executor, 10L);

    WorkflowInstanceVO started = projectContext.call(
        PROJECT,
        () -> runtime.run(request("cached-project-required", 0L)));

    assertThatThrownBy(() -> runtime.getInstance(started.id()))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void propagatesProjectToAsynchronousTaskStartAndPolling() throws InterruptedException {
    TestProjectContext projectContext = new TestProjectContext();
    ProjectCheckingTaskExecutor executor = new ProjectCheckingTaskExecutor(projectContext, 80L);
    runtime = runtime(projectContext, executor, 5L);

    WorkflowInstanceVO started = projectContext.call(
        PROJECT,
        () -> runtime.run(request("project-propagation", 0L)));
    projectContext.run(PROJECT, () -> runtime.activate(started.id()));

    WorkflowInstanceVO completed = waitForTerminal(projectContext, started.id(), 2_000L);

    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(executor.startProjects()).containsExactly(7L);
    assertThat(executor.statusProjects()).isNotEmpty().allMatch(projectId -> projectId == 7L);
  }

  @Test
  void propagatesProjectToTimeoutScannerAndRemoteCancellation() throws InterruptedException {
    TestProjectContext projectContext = new TestProjectContext();
    ProjectCheckingTaskExecutor executor = new ProjectCheckingTaskExecutor(projectContext, 5_000L);
    runtime = runtime(projectContext, executor, 10L);

    WorkflowInstanceVO started = projectContext.call(
        PROJECT,
        () -> runtime.run(request("project-timeout", 1L)));
    projectContext.run(PROJECT, () -> runtime.activate(started.id()));

    WorkflowInstanceVO completed = waitForTerminal(projectContext, started.id(), 3_000L);
    waitForCancellation(executor, 1_000L);

    assertThat(completed.status()).isEqualTo("TIMED_OUT");
    assertThat(executor.cancelProjects()).contains(7L);
    assertThat(executor.cancelCount()).isGreaterThanOrEqualTo(1);
  }

  private WorkflowRuntime runtime(
      TestProjectContext projectContext,
      ProjectCheckingTaskExecutor executor,
      long pollIntervalMillis) {
    return runtime(projectContext, executor, registry(), pollIntervalMillis);
  }

  private WorkflowRuntime runtime(
      TestProjectContext projectContext,
      ProjectCheckingTaskExecutor executor,
      TaskRegistry registry,
      long pollIntervalMillis) {
    return new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        new TaskExecutionGateway(List.of(executor)),
        pollIntervalMillis,
        projectContext,
        projectContext);
  }

  private TaskRegistry registry() {
    return new TaskRegistry() {
      private final TaskDefinition task = new TaskDefinition("task-a", "task-a", "SYNC");

      @Override
      public List<TaskDefinition> list() {
        return List.of(task);
      }

      @Override
      public TaskDefinition get(String taskId) {
        if (!task.id().equals(taskId)) {
          throw new IllegalArgumentException("任务不存在：" + taskId);
        }
        return task;
      }
    };
  }

  private WorkflowRunDTO request(String name, long timeoutSeconds) {
    NodeDTO node = new NodeDTO(
        "node-a",
        "task-a",
        1,
        0L,
        0L,
        0L,
        Map.of(),
        "ALL_SUCCESS",
        "FAIL_WORKFLOW");
    return new WorkflowRunDTO(
        name,
        List.of(node),
        List.of(),
        Map.of(),
        timeoutSeconds,
        "CONTINUE_INDEPENDENT_BRANCHES");
  }

  private WorkflowInstanceVO waitForTerminal(
      TestProjectContext projectContext,
      String executionId,
      long timeoutMillis) throws InterruptedException {
    long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
    WorkflowInstanceVO current = projectContext.call(
        PROJECT, () -> runtime.getInstance(executionId));
    while (!TERMINAL.contains(current.status()) && System.nanoTime() < deadline) {
      Thread.sleep(10L);
      current = projectContext.call(PROJECT, () -> runtime.getInstance(executionId));
    }
    return current;
  }

  private void waitForCancellation(ProjectCheckingTaskExecutor executor, long timeoutMillis)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
    while (executor.cancelCount() == 0 && System.nanoTime() < deadline) {
      Thread.sleep(5L);
    }
  }

  private static final class TestProjectContext
      implements CurrentProject, ProjectContextScope {
    private final ThreadLocal<ProjectContext> holder = new ThreadLocal<>();

    @Override
    public Optional<ProjectContext> current() {
      return Optional.ofNullable(holder.get());
    }

    @Override
    public <T> T call(ProjectContext context, java.util.function.Supplier<T> action) {
      ProjectContext previous = holder.get();
      holder.set(context);
      try {
        return action.get();
      } finally {
        if (previous == null) holder.remove();
        else holder.set(previous);
      }
    }
  }

  private static final class ProjectAwareRuntimeRepository
      implements WorkflowRuntimeRepository {
    private final CurrentProject currentProject;
    private final InMemoryWorkflowRuntimeRepository delegate =
        new InMemoryWorkflowRuntimeRepository();
    private final ConcurrentMap<String, Long> executionProjects = new ConcurrentHashMap<>();

    private ProjectAwareRuntimeRepository(CurrentProject currentProject) {
      this.currentProject = currentProject;
    }

    @Override
    public void prepareMetadata(String definitionId, RuntimeMetadataRecord metadata) {
      currentProject.requireProjectId();
      delegate.prepareMetadata(definitionId, metadata);
    }

    @Override
    public void saveMetadata(String executionId, RuntimeMetadataRecord metadata) {
      executionProjects.put(executionId, currentProject.requireProjectId());
      delegate.saveMetadata(executionId, metadata);
    }

    @Override
    public Optional<RuntimeMetadataRecord> findMetadata(String executionId) {
      Long owner = executionProjects.get(executionId);
      if (owner == null || !owner.equals(currentProject.requireProjectId())) {
        return Optional.empty();
      }
      return delegate.findMetadata(executionId);
    }

    @Override
    public List<String> listExecutionIds() {
      long projectId = currentProject.requireProjectId();
      return delegate.listExecutionIds().stream()
          .filter(id -> Long.valueOf(projectId).equals(executionProjects.get(id)))
          .toList();
    }

    @Override
    public List<String> findRecoverableExecutionIds() {
      long projectId = currentProject.requireProjectId();
      return delegate.findRecoverableExecutionIds().stream()
          .filter(id -> Long.valueOf(projectId).equals(executionProjects.get(id)))
          .toList();
    }

    @Override
    public void bindExternalExecution(String attemptId, String externalExecutionId) {
      currentProject.requireProjectId();
      delegate.bindExternalExecution(attemptId, externalExecutionId);
    }

    @Override
    public Optional<String> findExternalExecution(String attemptId) {
      currentProject.requireProjectId();
      return delegate.findExternalExecution(attemptId);
    }
  }

  private static final class ProjectCheckingTaskExecutor implements TaskExecutor {
    private final CurrentProject currentProject;
    private final long durationMillis;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger cancels = new AtomicInteger();
    private final List<Long> startProjects = new CopyOnWriteArrayList<>();
    private final List<Long> statusProjects = new CopyOnWriteArrayList<>();
    private final List<Long> cancelProjects = new CopyOnWriteArrayList<>();
    private volatile State state;

    private ProjectCheckingTaskExecutor(CurrentProject currentProject, long durationMillis) {
      this.currentProject = currentProject;
      this.durationMillis = durationMillis;
    }

    List<Long> startProjects() {
      return List.copyOf(startProjects);
    }

    List<Long> statusProjects() {
      return List.copyOf(statusProjects);
    }

    List<Long> cancelProjects() {
      return List.copyOf(cancelProjects);
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
      startProjects.add(currentProject.requireProjectId());
      State created = new State(
          "sync-" + sequence.incrementAndGet(),
          taskId,
          System.nanoTime(),
          durationMillis);
      state = created;
      return view(created);
    }

    @Override
    public TaskExecution status(String executionId) {
      statusProjects.add(currentProject.requireProjectId());
      State current = state;
      if (current == null || !current.executionId().equals(executionId)) {
        throw new IllegalArgumentException("execution not found: " + executionId);
      }
      return view(current);
    }

    @Override
    public void cancel(String executionId) {
      cancelProjects.add(currentProject.requireProjectId());
      State current = state;
      if (current != null && current.executionId().equals(executionId)) {
        current.canceled = true;
        cancels.incrementAndGet();
      }
    }

    private TaskExecution view(State current) {
      long elapsedMillis = (System.nanoTime() - current.startedAtNanos()) / 1_000_000L;
      String status = current.canceled
          ? "CANCELED"
          : elapsedMillis >= current.durationMillis() ? "SUCCEEDED" : "RUNNING";
      return new TaskExecution(
          current.executionId(),
          status,
          null,
          Map.of("taskId", current.taskId()));
    }

    private static final class State {
      private final String executionId;
      private final String taskId;
      private final long startedAtNanos;
      private final long durationMillis;
      private volatile boolean canceled;

      private State(
          String executionId,
          String taskId,
          long startedAtNanos,
          long durationMillis) {
        this.executionId = executionId;
        this.taskId = taskId;
        this.startedAtNanos = startedAtNanos;
        this.durationMillis = durationMillis;
      }

      String executionId() {
        return executionId;
      }

      String taskId() {
        return taskId;
      }

      long startedAtNanos() {
        return startedAtNanos;
      }

      long durationMillis() {
        return durationMillis;
      }
    }
  }
}
