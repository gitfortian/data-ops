package io.yak.ops.business.sync.offline.reconcile;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpNodeResponse;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository.ProjectExecutionRef;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Reconciles active Attempts with the configured Link-Up instance and schedules frozen retries. */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineExecutionReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(OfflineExecutionReconciler.class);
  private static final long MIN_UNKNOWN_DEADLINE_MILLIS = 1_000L;

  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineJobExecutionService executionService;
  private final LinkUpClient linkUpClient;
  private final OfflineSyncProperties properties;
  private final ProjectContextScope projectScope;

  @Scheduled(
      initialDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}",
      fixedDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}")
  public void reconcile() {
    int limit = Math.max(1, properties.getControl().getScanBatchSize());
    List<ProjectExecutionRef> active =
        executionRepository.findActiveExecutionsForReconciliation(limit);
    LinkUpProbe probe = probeLinkUp();

    for (ProjectExecutionRef candidate : active) {
      runInProject(candidate, execution -> reconcileExecution(execution, probe));
    }

    LocalDateTime now = LocalDateTime.now();
    for (ProjectExecutionRef candidate :
        executionRepository.findRetryCandidatesForReconciliation(now, limit)) {
      runInProject(candidate, this::retry);
    }
  }

  private void runInProject(
      ProjectExecutionRef candidate,
      java.util.function.Consumer<OfflineJobExecution> action) {
    try {
      projectScope.run(
          new ProjectContext(candidate.projectId(), null),
          () -> {
            OfflineJobExecution execution =
                executionRepository.findById(candidate.executionId()).orElse(null);
            if (execution != null) action.accept(execution);
          });
    } catch (RuntimeException exception) {
      LOG.warn(
          "Offline execution background processing failed, projectId={}, executionId={}",
          candidate.projectId(),
          candidate.executionId(),
          exception);
    }
  }

  private LinkUpProbe probeLinkUp() {
    try {
      return LinkUpProbe.available(linkUpClient.node());
    } catch (RuntimeException exception) {
      return LinkUpProbe.unavailable(exception);
    }
  }

  private void reconcileExecution(OfflineJobExecution execution, LinkUpProbe probe) {
    try {
      probe.requireAvailable();
      if (workerChanged(execution, probe.node())) {
        if (OfflineExecutionStatus.parse(execution.getStatus()) != OfflineExecutionStatus.UNKNOWN) {
          executionService.markUnknown(
              execution, "Link-Up instanceId 已变化，旧实例执行结果无法继续确认");
        }
        return;
      }

      if (executionService.reconcileFanOutIfNeeded(execution)) {
        return;
      }

      LinkUpJobResponse response = queryExecution(execution);
      executionService.applySnapshot(execution, response, "RECONCILED");
      reconcileCancellation(execution, response);
    } catch (RuntimeException exception) {
      handleReconcileFailure(execution, exception);
    }
  }

  private boolean workerChanged(OfflineJobExecution execution, LinkUpNodeResponse node) {
    return node != null
        && StringUtils.hasText(execution.getWorkerInstanceId())
        && StringUtils.hasText(node.getInstanceId())
        && !execution.getWorkerInstanceId().equals(node.getInstanceId());
  }

  private LinkUpJobResponse queryExecution(OfflineJobExecution execution) {
    if (StringUtils.hasText(execution.getEngineJobId())) {
      return linkUpClient.getJob(execution.getEngineJobId());
    }
    return linkUpClient.findByExternalExecutionId(execution.getExternalExecutionId());
  }

  private void reconcileCancellation(
      OfflineJobExecution execution, LinkUpJobResponse response) {
    if (!Boolean.TRUE.equals(execution.getCancellationRequested())
        || !StringUtils.hasText(execution.getEngineJobId())
        || response == null
        || !OfflineExecutionStatus.isConfirmedActive(response.getStatus())) {
      return;
    }

    executionService.applySnapshot(
        execution,
        linkUpClient.cancel(execution.getEngineJobId()),
        "CANCEL_RECONCILED");
  }

  private void handleReconcileFailure(
      OfflineJobExecution execution, RuntimeException exception) {
    if (isPastUnknownDeadline(execution)) {
      executionService.markUnknown(
          execution, "Link-Up 状态对账超时：" + safeMessage(exception));
      return;
    }
    LOG.debug("Offline execution reconcile failed, executionId={}", execution.getId(), exception);
  }

  private void retry(OfflineJobExecution execution) {
    try {
      executionService.retryFrom(execution);
    } catch (RuntimeException exception) {
      LOG.warn("Offline execution retry failed, executionId={}", execution.getId(), exception);
    }
  }

  private boolean isPastUnknownDeadline(OfflineJobExecution execution) {
    LocalDateTime reference =
        execution.getLastSyncTime() == null ? execution.getCreateTime() : execution.getLastSyncTime();
    if (reference == null) {
      return false;
    }
    long deadline =
        Math.max(MIN_UNKNOWN_DEADLINE_MILLIS, properties.getControl().getLostAfterMillis());
    return Duration.between(reference, LocalDateTime.now()).toMillis() >= deadline;
  }

  private String safeMessage(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }

  private record LinkUpProbe(LinkUpNodeResponse node, RuntimeException error) {

    private static LinkUpProbe available(LinkUpNodeResponse node) {
      return new LinkUpProbe(node, null);
    }

    private static LinkUpProbe unavailable(RuntimeException error) {
      return new LinkUpProbe(null, error);
    }

    private void requireAvailable() {
      if (error != null) {
        throw error;
      }
    }
  }
}
