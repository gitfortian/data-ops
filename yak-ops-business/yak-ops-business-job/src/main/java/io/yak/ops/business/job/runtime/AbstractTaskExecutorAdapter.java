package io.yak.ops.business.job.runtime;

import static io.yak.ops.plugin.task.api.ScriptTaskSupport.hasResourceReference;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.safeMessage;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.summarizeIssues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.DefaultTaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.resource.ResourceResolver;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/** Shared local runtime for TaskPlugin-backed task types. */
public abstract class AbstractTaskExecutorAdapter implements TaskExecutor {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final TaskPluginRegistry pluginRegistry;
  private final ObjectProvider<ResourceResolver> resourceResolverProvider;
  private final ObjectMapper objectMapper;
  private final TaskExecutionContextFactory contextFactory;
  private final ExecutorService workerExecutor;
  private final ConcurrentMap<String, ExecutionHandle> executions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, CompletableFuture<String>> idempotencyStarts =
      new ConcurrentHashMap<>();

  protected AbstractTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<ResourceResolver> resourceResolverProvider,
      ObjectMapper objectMapper,
      TaskExecutionContextFactory contextFactory) {
    this.pluginRegistry = pluginRegistry;
    this.resourceResolverProvider = resourceResolverProvider;
    this.objectMapper = objectMapper;
    this.contextFactory = contextFactory;
    this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public abstract String taskType();

  protected abstract String executionIdPrefix();

  protected abstract String displayName();

  /** Contributes task-type-specific runtime capabilities. */
  protected void configureContext(
      DefaultTaskExecutionContext.Builder builder,
      String definitionJson) {
    if (resourceResolverProvider == null) return;
    if (hasResourceReference(definitionJson, objectMapper)) {
      ResourceResolver resolver = resourceResolverProvider.getIfAvailable();
      if (resolver != null) builder.capability(ResourceResolver.class, resolver);
    }
  }

  protected String snapshotRequiredMessage() {
    return "Task version snapshot must not be null";
  }

  protected String snapshotTypeMismatchMessage(TaskVersionSnapshot snapshot) {
    return displayName() + " executor cannot execute task type: " + snapshot.type();
  }

  protected String missingDefinitionSnapshotMessage(TaskVersionSnapshot snapshot) {
    return displayName() + " task missing immutable definitionSnapshot: " + snapshot.taskId();
  }

  protected String executionNotFoundMessage(String executionId) {
    return displayName() + " task execution not found: " + executionId;
  }

  protected String pluginNotExecutableMessage() {
    return displayName() + " Task Plugin does not support execution";
  }

  protected String validationFailureMessage(TaskValidationResult validation) {
    return summarizeIssues(validation, displayName() + " task validation failed");
  }

  protected String executionFailureMessage(Throwable throwable) {
    return safeMessage(throwable, displayName() + " execution failed");
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
    requireSnapshot(snapshot);

    TaskExecutionTrigger safeTrigger =
        trigger == null ? TaskExecutionTrigger.WORKFLOW : trigger;
    String safeKey = normalizeKey(idempotencyKey);
    if (safeKey == null) {
      return startNewExecution(snapshot, safeTrigger, null, input);
    }

    String existing = idempotencyIndex.get(safeKey);
    if (existing != null) return status(existing);

    CompletableFuture<String> owner = new CompletableFuture<>();
    CompletableFuture<String> inFlight = idempotencyStarts.putIfAbsent(safeKey, owner);
    if (inFlight != null) {
      log.debug(
          "{} task startup waiting for idempotent owner task={} idempotencyKey={}",
          displayName(),
          snapshot.taskId(),
          safeKey);
      return status(awaitExecutionId(inFlight));
    }

    try {
      existing = idempotencyIndex.get(safeKey);
      if (existing != null) {
        owner.complete(existing);
        return status(existing);
      }
      TaskExecution started = startNewExecution(snapshot, safeTrigger, safeKey, input);
      owner.complete(started.executionId());
      return started;
    } catch (RuntimeException | Error failure) {
      owner.completeExceptionally(failure);
      throw failure;
    } finally {
      idempotencyStarts.remove(safeKey, owner);
    }
  }

  @Override
  public TaskExecution status(String executionId) {
    return requireHandle(executionId).snapshot().get();
  }

  @Override
  public void cancel(String executionId) {
    ExecutionHandle handle = requireHandle(executionId);
    TaskExecution current = handle.snapshot().get();
    if (current.terminal()) return;

    handle.executor().cancel();
    handle.snapshot().updateAndGet(existing -> existing.terminal()
        ? existing
        : new TaskExecution(
            executionId,
            "CANCELED",
            displayName() + " execution cancelled",
            existing.output()));
  }

  @PreDestroy
  public void shutdown() {
    workerExecutor.shutdownNow();
  }

  protected ResourceResolver resourceResolver() {
    return resourceResolverProvider == null ? null : resourceResolverProvider.getIfAvailable();
  }

  private TaskExecution startNewExecution(
      TaskVersionSnapshot snapshot,
      TaskExecutionTrigger trigger,
      String idempotencyKey,
      Map<String, Object> input) {
    long startedAtNanos = System.nanoTime();
    try {
      logStartupStage("definition", snapshot, idempotencyKey, startedAtNanos);
      TaskDefinition definition = extractDefinition(snapshot);
      TaskPlugin plugin = requireExecutablePlugin();
      validateDefinition(plugin, definition);

      logStartupStage("context", snapshot, idempotencyKey, startedAtNanos);
      TaskExecutionContext context = contextFactory.create(
          trigger,
          input,
          builder -> configureContext(builder, snapshot.definitionSnapshotJson()));

      logStartupStage("executor", snapshot, idempotencyKey, startedAtNanos);
      io.yak.ops.plugin.task.api.TaskExecutor pluginExecutor =
          plugin.createExecutor(definition, context);

      String executionId = executionIdPrefix() + "-" + UUID.randomUUID();
      TaskExecution initial = new TaskExecution(executionId, "RUNNING", null, Map.of());
      ExecutionHandle handle = new ExecutionHandle(pluginExecutor, new AtomicReference<>(initial));
      executions.put(executionId, handle);
      if (idempotencyKey != null) idempotencyIndex.put(idempotencyKey, executionId);

      logStartupStage("worker-submit", snapshot, idempotencyKey, startedAtNanos);
      try {
        workerExecutor.submit(
            contextFactory.captureProjectContext(() -> runExecution(executionId, handle)));
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (Throwable throwable) {
        TaskExecution failed = new TaskExecution(
            executionId,
            "FAILED",
            executionFailureMessage(throwable),
            Map.of());
        handle.snapshot().set(failed);
        log.error(
            "{} task worker submission failed [{}]",
            displayName(),
            executionId,
            throwable);
        return failed;
      }
      logStartupStage("started", snapshot, idempotencyKey, startedAtNanos);
      return initial;
    } catch (VirtualMachineError | ThreadDeath fatal) {
      throw fatal;
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException(executionFailureMessage(throwable), throwable);
    }
  }

  private void runExecution(String executionId, ExecutionHandle handle) {
    try {
      TaskExecutionResult result = handle.executor().execute();
      TaskExecution completed = convert(executionId, result);
      handle.snapshot().updateAndGet(current -> current.terminal() ? current : completed);
      if (completed.terminal() && !"SUCCEEDED".equals(completed.status())) {
        log.warn(
            "{} task execution ended [{}] status={} message={}",
            displayName(),
            executionId,
            completed.status(),
            completed.errorMessage());
      } else {
        log.info(
            "{} task execution completed [{}] status={}",
            displayName(),
            executionId,
            completed.status());
      }
    } catch (VirtualMachineError | ThreadDeath fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      log.error("{} task execution exception [{}]", displayName(), executionId, throwable);
      TaskExecution failed = new TaskExecution(
          executionId,
          "FAILED",
          executionFailureMessage(throwable),
          Map.of());
      handle.snapshot().updateAndGet(current -> current.terminal() ? current : failed);
    }
  }

  private static TaskExecution convert(String executionId, TaskExecutionResult result) {
    String status = switch (result.status()) {
      case PENDING -> "PENDING";
      case RUNNING -> "RUNNING";
      case SUCCESS -> "SUCCEEDED";
      case FAILED -> "FAILED";
      case CANCELLED -> "CANCELED";
      case TIMEOUT -> "TIMED_OUT";
    };
    String errorMessage =
        result.status() == TaskExecutionStatus.SUCCESS ? null : result.message();
    return new TaskExecution(executionId, status, errorMessage, result.output());
  }

  private TaskPlugin requireExecutablePlugin() {
    TaskPlugin plugin = pluginRegistry.require(taskType());
    if (!plugin.descriptor().executable()) {
      throw new IllegalStateException(pluginNotExecutableMessage());
    }
    return plugin;
  }

  private TaskDefinition extractDefinition(TaskVersionSnapshot snapshot) {
    String json = snapshot.definitionSnapshotJson();
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException(missingDefinitionSnapshotMessage(snapshot));
    }
    try {
      TaskDefinition definition = objectMapper.readValue(json, TaskDefinition.class);
      if (!taskType().equalsIgnoreCase(definition.taskType())) {
        throw new IllegalArgumentException(
            displayName() + " task snapshot type mismatch: snapshot="
                + taskType() + ", definition=" + definition.taskType());
      }
      return definition;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          displayName() + " task definitionSnapshot is not valid JSON", exception);
    }
  }

  private void validateDefinition(TaskPlugin plugin, TaskDefinition definition) {
    TaskValidationResult validation = plugin.validate(definition);
    String summary = validationFailureMessage(validation);
    if (summary != null) throw new IllegalArgumentException(summary);
  }

  private void requireSnapshot(TaskVersionSnapshot snapshot) {
    if (snapshot == null) throw new IllegalArgumentException(snapshotRequiredMessage());
    if (!taskType().equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException(snapshotTypeMismatchMessage(snapshot));
    }
  }

  private ExecutionHandle requireHandle(String executionId) {
    ExecutionHandle handle = executions.get(executionId);
    if (handle == null) throw new IllegalArgumentException(executionNotFoundMessage(executionId));
    return handle;
  }

  private String awaitExecutionId(CompletableFuture<String> inFlight) {
    try {
      return inFlight.join();
    } catch (CompletionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) throw runtimeException;
      if (cause instanceof Error error) throw error;
      throw new IllegalStateException(cause == null ? exception : cause);
    }
  }

  private void logStartupStage(
      String stage,
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      long startedAtNanos) {
    if (!log.isDebugEnabled()) return;
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    log.debug(
        "{} task startup stage={} task={} idempotencyKey={} elapsedMs={}",
        displayName(),
        stage,
        snapshot.taskId(),
        idempotencyKey,
        elapsedMillis);
  }

  private static String normalizeKey(String key) {
    if (key == null || key.isBlank()) return null;
    return key.trim();
  }

  protected record ExecutionHandle(
      io.yak.ops.plugin.task.api.TaskExecutor executor,
      AtomicReference<TaskExecution> snapshot) {}
}
