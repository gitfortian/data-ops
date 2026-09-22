package io.yak.ops.business.development.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSubmission;
import io.yak.ops.business.development.execution.model.DevelopmentTaskRunResult;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.task.DevelopmentTaskDefinitionNormalizer;
import io.yak.ops.business.development.task.DevelopmentTaskNodeResolver;
import io.yak.ops.business.development.task.DevelopmentTaskValidationException;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Submits current editor definitions through the shared Task Runtime. */
@Service
public class DevelopmentTaskRunService {

  private final DevelopmentTaskNodeResolver nodes;
  private final DevelopmentTaskDefinitionNormalizer definitions;
  private final TaskExecutionGateway taskExecutionGateway;
  private final DevelopmentTaskExecutionService executionService;
  private final ObjectMapper objectMapper;

  /** Keeps focused tests and existing non-Spring callers source compatible. */
  public DevelopmentTaskRunService(
      DevelopmentNodeRepository nodeRepository,
      TaskExecutionGateway taskExecutionGateway,
      DevelopmentTaskExecutionService executionService,
      ObjectMapper objectMapper) {
    this(
        new DevelopmentTaskNodeResolver(nodeRepository),
        new DevelopmentTaskDefinitionNormalizer(objectMapper),
        taskExecutionGateway,
        executionService,
        objectMapper);
  }

  @Autowired
  public DevelopmentTaskRunService(
      DevelopmentTaskNodeResolver nodes,
      DevelopmentTaskDefinitionNormalizer definitions,
      TaskExecutionGateway taskExecutionGateway,
      DevelopmentTaskExecutionService executionService,
      ObjectMapper objectMapper) {
    this.nodes = nodes;
    this.definitions = definitions;
    this.taskExecutionGateway = taskExecutionGateway;
    this.executionService = executionService;
    this.objectMapper = objectMapper;
  }

  public DevelopmentTaskExecutionSubmission submit(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      String operatorName) {
    return submit(nodeId, taskType, schemaVersion, content, configJson, operatorName, null);
  }

  public DevelopmentTaskExecutionSubmission submit(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      String operatorName,
      Long retryOfExecutionId) {
    DevelopmentNode node = nodes.requireNode(nodeId);
    TaskDefinition definition =
        definitions.normalize(node, taskType, schemaVersion, content, configJson);
    requireRuntime(definition);

    long started = System.nanoTime();
    long historyId = executionService.createPending(
        node,
        definition.taskType(),
        schemaVersion,
        definition.content(),
        definition.configJson(),
        operatorName,
        retryOfExecutionId);
    try {
      TaskVersionSnapshot snapshot = currentDraftSnapshot(node, definition);
      TaskExecution execution = taskExecutionGateway.start(
          snapshot,
          TaskExecutionTrigger.MANUAL,
          null,
          Map.of("nodeId", String.valueOf(nodeId), "executionHistoryId", String.valueOf(historyId)));
      TaskExecutionStatus status = mapStatus(execution);
      executionService.attachRuntime(historyId, execution.executionId(), status.name());
      if (terminal(status)) {
        executionService.complete(
            historyId,
            status.name(),
            elapsedMillis(started),
            execution.errorMessage(),
            execution.output());
      }
      return new DevelopmentTaskExecutionSubmission(
          historyId,
          node.id(),
          definition.taskType(),
          execution.executionId(),
          status);
    } catch (DevelopmentTaskValidationException exception) {
      executionService.complete(
          historyId,
          TaskExecutionStatus.FAILED.name(),
          elapsedMillis(started),
          safeMessage(exception),
          Map.of());
      throw exception;
    } catch (IllegalArgumentException exception) {
      String message = safeMessage(exception);
      executionService.complete(
          historyId,
          TaskExecutionStatus.FAILED.name(),
          elapsedMillis(started),
          message,
          Map.of());
      throw new DevelopmentTaskValidationException(
          message,
          List.of(new TaskValidationIssue(
              "TASK_RUNTIME_VALIDATION_FAILED",
              "definition",
              message)));
    } catch (Exception exception) {
      String message = safeMessage(exception);
      executionService.complete(
          historyId,
          TaskExecutionStatus.FAILED.name(),
          elapsedMillis(started),
          message,
          Map.of());
      return new DevelopmentTaskExecutionSubmission(
          historyId,
          node.id(),
          definition.taskType(),
          null,
          TaskExecutionStatus.FAILED);
    }
  }

  /**
   * Legacy synchronous corridor for focused internal callers. HTTP editor runs use {@link #submit} and
   * must not wait for terminal runtime state.
   */
  @Deprecated
  public DevelopmentTaskRunResult run(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson) {
    return run(nodeId, taskType, schemaVersion, content, configJson, "unknown");
  }

  @Deprecated
  public DevelopmentTaskRunResult run(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      String operatorName) {
    long started = System.nanoTime();
    DevelopmentTaskExecutionSubmission submission =
        submit(nodeId, taskType, schemaVersion, content, configJson, operatorName);
    if (terminal(submission.status()) || submission.runtimeExecutionId() == null) {
      return resultFromHistory(executionService.get(submission.id()));
    }

    TaskExecution completed = awaitTerminal(submission.taskType(), submission.runtimeExecutionId());
    TaskExecutionStatus status = mapStatus(completed);
    long durationMs = elapsedMillis(started);
    executionService.complete(
        submission.id(),
        status.name(),
        durationMs,
        completed.errorMessage(),
        completed.output());
    return new DevelopmentTaskRunResult(
        status,
        completed.errorMessage(),
        durationMs,
        completed.output());
  }

  static TaskExecutionStatus mapStatus(TaskExecution execution) {
    String status = execution.status() == null
        ? ""
        : execution.status().trim().toUpperCase(Locale.ROOT);
    return switch (status) {
      case "PENDING" -> TaskExecutionStatus.PENDING;
      case "RUNNING" -> TaskExecutionStatus.RUNNING;
      case "SUCCEEDED" -> TaskExecutionStatus.SUCCESS;
      case "CANCELED" -> TaskExecutionStatus.CANCELLED;
      case "TIMED_OUT" -> TaskExecutionStatus.TIMEOUT;
      default -> TaskExecutionStatus.FAILED;
    };
  }

  static boolean terminal(TaskExecutionStatus status) {
    return status != TaskExecutionStatus.PENDING && status != TaskExecutionStatus.RUNNING;
  }

  private void requireRuntime(TaskDefinition definition) {
    if (!taskExecutionGateway.supports(definition.taskType())) {
      throw new DevelopmentTaskValidationException(
          "当前未安装 " + definition.taskType() + " Task Runtime，无法运行",
          List.of(new TaskValidationIssue(
              "TASK_RUNTIME_NOT_INSTALLED",
              "taskType",
              "Task runtime is not installed: " + definition.taskType())));
    }
  }

  private TaskVersionSnapshot currentDraftSnapshot(
      DevelopmentNode node,
      TaskDefinition definition) {
    try {
      return new TaskVersionSnapshot(
          "development:" + node.id(),
          node.name(),
          definition.taskType(),
          0L,
          null,
          objectMapper.writeValueAsString(definition),
          definition.configJson());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("当前任务定义无法序列化为运行时快照", exception);
    }
  }

  private TaskExecution awaitTerminal(String taskType, String executionId) {
    TaskExecution current = taskExecutionGateway.status(taskType, executionId);
    while (!current.terminal()) {
      try {
        Thread.sleep(10L);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        try {
          taskExecutionGateway.cancel(taskType, current.executionId());
        } catch (RuntimeException ignored) {
          // Preserve the original interruption as the legacy manual-run result.
        }
        throw new IllegalStateException("等待任务执行结果时被中断", exception);
      }
      current = taskExecutionGateway.status(taskType, current.executionId());
    }
    return current;
  }

  private DevelopmentTaskRunResult resultFromHistory(DevelopmentTaskExecutionDetail detail) {
    TaskExecutionStatus status;
    try {
      status = TaskExecutionStatus.valueOf(detail.status());
    } catch (RuntimeException exception) {
      status = TaskExecutionStatus.FAILED;
    }
    return new DevelopmentTaskRunResult(
        status,
        detail.errorMessage(),
        detail.durationMs() == null ? 0L : detail.durationMs(),
        detail.output());
  }

  private long elapsedMillis(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "任务执行失败" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
