package io.yak.ops.business.job.adapter.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.environment.SystemEnvVarService;
import io.yak.ops.business.job.runtime.TaskExecutionContextFactory;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SqlTaskExecutorAdapterTest {

  private SqlTaskExecutorAdapter adapter;

  @AfterEach
  void tearDown() {
    if (adapter != null) adapter.shutdown();
  }

  @Test
  void executesDefinitionFromImmutableSnapshot() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        TaskExecutionResult.success(Map.of("version", "v1")));
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        stubContextFactory());

    TaskDefinition frozenV1 = new TaskDefinition(
        "SQL",
        1,
        "select 1 as version",
        "{\"dataSourceId\":\"1\"}");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "task-asset:12",
        "今天统计",
        "SQL",
        1L,
        "checksum-v1",
        objectMapper.writeValueAsString(frozenV1),
        frozenV1.configJson());

    TaskExecution started = adapter.start(snapshot, "attempt-1", Map.of("bizDate", "2026-08-12"));
    TaskExecution completed = awaitTerminal(started.executionId());

    assertTrue(completed.successful());
    assertEquals("v1", completed.output().get("version"));
    assertEquals("select 1 as version", plugin.definition.get().content());
    assertEquals("2026-08-12", plugin.context.get().parameters().get("bizDate"));
    assertEquals(TaskExecutionTrigger.WORKFLOW, plugin.context.get().trigger());
  }

  @Test
  void propagatesManualTriggerToPluginContext() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        TaskExecutionResult.success(Map.of("ok", true)));
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        stubContextFactory());
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "development:1",
        "手动运行",
        "SQL",
        0L,
        null,
        objectMapper.writeValueAsString(definition),
        definition.configJson());

    TaskExecution started = adapter.start(
        snapshot,
        TaskExecutionTrigger.MANUAL,
        null,
        Map.of("nodeId", "1"));
    TaskExecution completed = awaitTerminal(started.executionId());

    assertTrue(completed.successful());
    assertEquals(TaskExecutionTrigger.MANUAL, plugin.context.get().trigger());
    assertEquals("1", plugin.context.get().parameters().get("nodeId"));
  }

  @Test
  void restoresCapturedProjectContextOnAsyncWorker() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    ThreadLocal<ProjectContext> projectHolder = new ThreadLocal<>();
    CurrentProject currentProject = () -> Optional.ofNullable(projectHolder.get());
    ProjectContextScope projectScope = new ProjectContextScope() {
      @Override
      public <T> T call(ProjectContext context, Supplier<T> action) {
        ProjectContext previous = projectHolder.get();
        projectHolder.set(context);
        try {
          return action.get();
        } finally {
          if (previous == null) {
            projectHolder.remove();
          } else {
            projectHolder.set(previous);
          }
        }
      }
    };
    AtomicReference<Long> executedProjectId = new AtomicReference<>();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(() -> {
      executedProjectId.set(currentProject.requireProjectId());
      return TaskExecutionResult.success(Map.of("ok", true));
    });
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        contextFactory(currentProject, projectScope));
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "development:42",
        "项目上下文传播",
        "SQL",
        0L,
        null,
        objectMapper.writeValueAsString(definition),
        definition.configJson());

    projectHolder.set(new ProjectContext(42L, "Project A"));
    TaskExecution started;
    try {
      started = adapter.start(
          snapshot,
          TaskExecutionTrigger.MANUAL,
          null,
          Map.of("nodeId", "42"));
    } finally {
      projectHolder.remove();
    }
    TaskExecution completed = awaitTerminal(started.executionId());

    assertTrue(completed.successful());
    assertEquals(42L, executedProjectId.get());
    assertTrue(currentProject.current().isEmpty());
  }

  @Test
  void rejectsMissingImmutableSnapshotWithoutFallback() {
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(new RecordingSqlPlugin(TaskExecutionResult.success(Map.of())))),
        emptyDataSourceProvider(),
        new ObjectMapper(),
        stubContextFactory());
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "task-asset:12", "今天统计", "SQL", 1L, "checksum-v1", null, null);

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> adapter.start(snapshot, "attempt-1", Map.of()));

    assertTrue(exception.getMessage().contains("拒绝回退"));
  }

  @Test
  void reusesExecutionForSameWorkflowAttempt() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        TaskExecutionResult.success(Map.of("ok", true)));
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        stubContextFactory());
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "task-asset:12",
        "SQL",
        "SQL",
        1L,
        "checksum-v1",
        objectMapper.writeValueAsString(definition),
        definition.configJson());

    TaskExecution first = adapter.start(snapshot, "same-attempt", Map.of());
    TaskExecution second = adapter.start(snapshot, "same-attempt", Map.of());

    assertEquals(first.executionId(), second.executionId());
    assertSame(plugin.definition.get(), plugin.definition.get());
  }

  @Test
  void doesNotSerializeDifferentWorkflowAttemptsDuringExecutorCreation() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    CountDownLatch blockingCreateEntered = new CountDownLatch(1);
    CountDownLatch releaseBlockingCreate = new CountDownLatch(1);
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        () -> TaskExecutionResult.success(Map.of("ok", true)),
        definition -> {
          if (!definition.content().contains("blocking")) return;
          blockingCreateEntered.countDown();
          awaitLatch(releaseBlockingCreate, "blocking SQL executor creation was not released");
        });
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        stubContextFactory());
    TaskVersionSnapshot blocking = snapshot(
        objectMapper, "task-asset:blocking", "select 'blocking'");
    TaskVersionSnapshot independent = snapshot(
        objectMapper, "task-asset:independent", "select 'independent'");
    ExecutorService callers = Executors.newFixedThreadPool(2);

    try {
      Future<TaskExecution> blockingStart = callers.submit(
          () -> adapter.start(blocking, "attempt-blocking", Map.of()));
      assertTrue(
          blockingCreateEntered.await(1, TimeUnit.SECONDS),
          "the first task should reach executor creation");

      Future<TaskExecution> independentStart = callers.submit(
          () -> adapter.start(independent, "attempt-independent", Map.of()));
      TaskExecution independentExecution;
      try {
        independentExecution = independentStart.get(2, TimeUnit.SECONDS);
      } finally {
        releaseBlockingCreate.countDown();
      }
      TaskExecution blockingExecution = blockingStart.get(2, TimeUnit.SECONDS);

      assertNotEquals(blockingExecution.executionId(), independentExecution.executionId());
      assertTrue(awaitTerminal(blockingExecution.executionId()).successful());
      assertTrue(awaitTerminal(independentExecution.executionId()).successful());
    } finally {
      releaseBlockingCreate.countDown();
      callers.shutdownNow();
    }
  }

  @Test
  void preservesIdempotencyForConcurrentCallsWithTheSameAttempt() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    CountDownLatch createEntered = new CountDownLatch(1);
    CountDownLatch releaseCreate = new CountDownLatch(1);
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        () -> TaskExecutionResult.success(Map.of("ok", true)),
        definition -> {
          createEntered.countDown();
          awaitLatch(releaseCreate, "SQL executor creation was not released");
        });
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        stubContextFactory());
    TaskVersionSnapshot snapshot = snapshot(
        objectMapper, "task-asset:idempotent", "select 1");
    ExecutorService callers = Executors.newFixedThreadPool(2);

    try {
      Future<TaskExecution> firstStart = callers.submit(
          () -> adapter.start(snapshot, "same-concurrent-attempt", Map.of()));
      assertTrue(createEntered.await(1, TimeUnit.SECONDS));
      Future<TaskExecution> secondStart = callers.submit(
          () -> adapter.start(snapshot, "same-concurrent-attempt", Map.of()));

      releaseCreate.countDown();
      TaskExecution first = firstStart.get(2, TimeUnit.SECONDS);
      TaskExecution second = secondStart.get(2, TimeUnit.SECONDS);

      assertEquals(first.executionId(), second.executionId());
      assertEquals(1, plugin.createCount.get());
    } finally {
      releaseCreate.countDown();
      callers.shutdownNow();
    }
  }

  @Test
  void convertsNonFatalPluginStartupErrorsToRuntimeFailures() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        () -> TaskExecutionResult.success(Map.of()),
        definition -> {
          throw new NoClassDefFoundError("missing-sql-driver");
        });
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        stubContextFactory());

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> adapter.start(
            snapshot(objectMapper, "task-asset:broken-start", "select 1"),
            "broken-start-attempt",
            Map.of()));

    assertTrue(exception.getMessage().contains("missing-sql-driver"));
  }

  @Test
  void marksNonFatalExecutionErrorsAsFailed() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(() -> {
      throw new NoClassDefFoundError("broken-sql-runtime");
    });
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper,
        stubContextFactory());

    TaskExecution started = adapter.start(
        snapshot(objectMapper, "task-asset:broken-runtime", "select 1"),
        "broken-runtime-attempt",
        Map.of());
    TaskExecution failed = awaitTerminal(started.executionId());

    assertEquals("FAILED", failed.status());
    assertTrue(failed.errorMessage().contains("broken-sql-runtime"));
  }

  private TaskExecution awaitTerminal(String executionId) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
    TaskExecution current = adapter.status(executionId);
    while (!current.terminal() && Instant.now().isBefore(deadline)) {
      Thread.sleep(10L);
      current = adapter.status(executionId);
    }
    assertTrue(current.terminal(), "SQL execution should become terminal");
    return current;
  }

  private TaskVersionSnapshot snapshot(
      ObjectMapper objectMapper,
      String taskId,
      String sql) throws Exception {
    TaskDefinition definition = new TaskDefinition("SQL", 1, sql, "{}");
    return new TaskVersionSnapshot(
        taskId,
        taskId,
        "SQL",
        1L,
        "checksum-" + taskId,
        objectMapper.writeValueAsString(definition),
        definition.configJson());
  }

  private static void awaitLatch(CountDownLatch latch, String timeoutMessage) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException(timeoutMessage);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for SQL startup", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<DataSourceExecutionProvider> emptyDataSourceProvider() {
    ObjectProvider<DataSourceExecutionProvider> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  private TaskExecutionContextFactory stubContextFactory() {
    SystemEnvVarService envVarService = mock(SystemEnvVarService.class);
    when(envVarService.resolveMergedEnv()).thenReturn(Map.of());
    return new TaskExecutionContextFactory(envVarService);
  }

  private TaskExecutionContextFactory contextFactory(
      CurrentProject currentProject,
      ProjectContextScope projectScope) {
    SystemEnvVarService envVarService = mock(SystemEnvVarService.class);
    when(envVarService.resolveMergedEnv()).thenReturn(Map.of());
    return new TaskExecutionContextFactory(envVarService, currentProject, projectScope);
  }

  private static final class RecordingSqlPlugin implements TaskPlugin {
    private final Supplier<TaskExecutionResult> resultSupplier;
    private final Consumer<TaskDefinition> beforeCreate;
    private final AtomicReference<TaskDefinition> definition = new AtomicReference<>();
    private final AtomicReference<TaskExecutionContext> context = new AtomicReference<>();
    private final AtomicInteger createCount = new AtomicInteger();

    private RecordingSqlPlugin(TaskExecutionResult result) {
      this(() -> result);
    }

    private RecordingSqlPlugin(Supplier<TaskExecutionResult> resultSupplier) {
      this(resultSupplier, ignored -> {});
    }

    private RecordingSqlPlugin(
        Supplier<TaskExecutionResult> resultSupplier,
        Consumer<TaskDefinition> beforeCreate) {
      this.resultSupplier = resultSupplier;
      this.beforeCreate = beforeCreate;
    }

    @Override
    public TaskPluginDescriptor descriptor() {
      return new TaskPluginDescriptor(
          "SQL", "SQL", "test SQL plugin", "1.0.0", 1, true, true);
    }

    @Override
    public TaskValidationResult validate(TaskDefinition definition) {
      return TaskValidationResult.ok();
    }

    @Override
    public io.yak.ops.plugin.task.api.TaskExecutor createExecutor(
        TaskDefinition definition,
        TaskExecutionContext context) {
      createCount.incrementAndGet();
      beforeCreate.accept(definition);
      this.definition.set(definition);
      this.context.set(context);
      return new io.yak.ops.plugin.task.api.TaskExecutor() {
        @Override
        public TaskExecutionResult execute() {
          return resultSupplier.get();
        }
      };
    }
  }
}
