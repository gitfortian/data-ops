package io.yak.ops.business.development.execution;

import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSubmission;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Runtime-facing control plane for durable data-development execution records. */
@Service
public class DevelopmentTaskExecutionControlService {

  private static final Duration UNATTACHED_RUNTIME_GRACE = Duration.ofSeconds(30);
  private static final Set<String> RETRYABLE = Set.of("FAILED", "CANCELLED", "TIMEOUT");

  private final DevelopmentTaskExecutionService histories;
  private final DevelopmentTaskRunService runService;
  private final TaskExecutionGateway taskExecutionGateway;
  private final ProjectContextScope projectContextScope;

  public DevelopmentTaskExecutionControlService(
      DevelopmentTaskExecutionService histories,
      DevelopmentTaskRunService runService,
      TaskExecutionGateway taskExecutionGateway,
      ProjectContextScope projectContextScope) {
    this.histories = histories;
    this.runService = runService;
    this.taskExecutionGateway = taskExecutionGateway;
    this.projectContextScope = projectContextScope;
  }

  public DevelopmentTaskExecutionDetail refresh(long id) {
    return reconcile(histories.get(id));
  }

  public Optional<DevelopmentTaskExecutionDetail> reattachActive(long nodeId) {
    return histories.findLatestActiveByNode(nodeId).map(this::reconcile);
  }

  public DevelopmentTaskExecutionDetail cancel(long id) {
    DevelopmentTaskExecutionDetail current = refresh(id);
    if (!active(current.status())) return current;
    if (current.runtimeExecutionId() == null || current.runtimeExecutionId().isBlank()) {
      histories.complete(
          current.id(),
          TaskExecutionStatus.CANCELLED.name(),
          durationMillis(current),
          "运行在接管前被取消",
          current.output());
      return histories.get(current.id());
    }

    try {
      taskExecutionGateway.cancel(current.taskType(), current.runtimeExecutionId());
    } catch (IllegalArgumentException exception) {
      return markRuntimeStateLost(current);
    }
    return refresh(current.id());
  }

  public DevelopmentTaskExecutionSubmission retry(long id, String operatorName) {
    DevelopmentTaskExecutionDetail previous = refresh(id);
    String status = normalize(previous.status());
    if (!RETRYABLE.contains(status)) {
      throw new IllegalArgumentException("仅失败、超时或已取消的运行记录可以重试");
    }
    return runService.submit(
        previous.nodeId(),
        previous.taskType(),
        previous.schemaVersion(),
        previous.content(),
        previous.configJson(),
        operatorName,
        previous.id());
  }

  void reconcileActiveExecutions(int limit) {
    for (DevelopmentTaskExecutionService.ReconciliationCandidate candidate
        : histories.listActiveForReconciliation(limit)) {
      try {
        projectContextScope.run(
            new ProjectContext(candidate.projectId(), null),
            () -> reconcile(candidate.execution()));
      } catch (RuntimeException ignored) {
        // One project/runtime failure must not block reconciliation for other executions.
      }
    }
  }

  DevelopmentTaskExecutionDetail reconcile(DevelopmentTaskExecutionDetail current) {
    if (!active(current.status())) return current;

    if (current.runtimeExecutionId() == null || current.runtimeExecutionId().isBlank()) {
      if (staleWithoutRuntime(current)) {
        histories.complete(
            current.id(),
            TaskExecutionStatus.FAILED.name(),
            durationMillis(current),
            "任务已创建但运行时未完成接管，可能在提交过程中发生服务重启",
            current.output());
        return histories.get(current.id());
      }
      return current;
    }

    final TaskExecution runtime;
    try {
      runtime = taskExecutionGateway.status(current.taskType(), current.runtimeExecutionId());
    } catch (IllegalArgumentException exception) {
      return markRuntimeStateLost(current);
    }

    TaskExecutionStatus status = DevelopmentTaskRunService.mapStatus(runtime);
    if (!DevelopmentTaskRunService.terminal(status)) {
      histories.updateActiveStatus(current.id(), status.name());
      return histories.get(current.id());
    }

    histories.complete(
        current.id(),
        status.name(),
        durationMillis(current),
        runtime.errorMessage(),
        runtime.output());
    return histories.get(current.id());
  }

  private DevelopmentTaskExecutionDetail markRuntimeStateLost(
      DevelopmentTaskExecutionDetail current) {
    histories.complete(
        current.id(),
        TaskExecutionStatus.FAILED.name(),
        durationMillis(current),
        "运行时状态不可用，任务可能因服务重启而中断",
        Map.of());
    return histories.get(current.id());
  }

  private boolean staleWithoutRuntime(DevelopmentTaskExecutionDetail current) {
    if (current.startTime() == null) return true;
    return Duration.between(current.startTime(), LocalDateTime.now())
        .compareTo(UNATTACHED_RUNTIME_GRACE) > 0;
  }

  private long durationMillis(DevelopmentTaskExecutionDetail current) {
    if (current.startTime() == null) return 0L;
    return Math.max(0L, Duration.between(current.startTime(), LocalDateTime.now()).toMillis());
  }

  private boolean active(String status) {
    String normalized = normalize(status);
    return "PENDING".equals(normalized) || "RUNNING".equals(normalized);
  }

  private String normalize(String status) {
    return status == null ? "" : status.trim().toUpperCase();
  }
}
