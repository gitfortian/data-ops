package io.yak.ops.business.sync.realtime.execution;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Owns SyncExecution state commits and exact external-stop handling. */
@Component
public class RealtimeExecutionStateManager {

  private static final int STOP_POLL_ATTEMPTS = 20;
  private static final long STOP_POLL_INTERVAL_MILLIS = 250L;

  private final RealtimeJobStore store;
  private final SyncExecutionStateMachine stateMachine;
  private final RealtimeEngineGateway gateway;
  private final RealtimeExecutionPreparation preparation;
  private final TransactionTemplate transactions;

  public RealtimeExecutionStateManager(
      RealtimeJobStore store,
      SyncExecutionStateMachine stateMachine,
      RealtimeEngineGateway gateway,
      RealtimeExecutionPreparation preparation,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.stateMachine = stateMachine;
    this.gateway = gateway;
    this.preparation = preparation;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  RealtimeJobView.Deployment completeStart(
      long taskId,
      long deploymentId,
      RealtimeExecutionPrepared prepared,
      RealtimeEngineGateway.DeployResult result) {
    Boolean cancelAfterSubmit =
        transactions.execute(
            status -> {
              store.lockDefinition(taskId);
              DeploymentRow row = requireCurrentExecutionRow(taskId, deploymentId);
              SyncExecution execution = row.execution();

              if (execution.desiredState() == DesiredState.RUNNING
                  && execution.observedState() == ObservedState.STARTING) {
                stateMachine.requireTransition(execution, ObservedState.RUNNING);
                store.markDeploymentRunning(
                    taskId,
                    deploymentId,
                    result.jobId(),
                    prepared.runtimeEnvironment().runtimeRevision());
                store.event(
                    taskId,
                    deploymentId,
                    "STARTED",
                    ObservedState.STARTING.name(),
                    ObservedState.RUNNING.name(),
                    "Flink 已接受任务");
                return false;
              }

              ObservedState from = execution.observedState();
              if (from != ObservedState.STOPPING) {
                stateMachine.requireTransition(execution, ObservedState.STOPPING);
                store.markStopping(taskId, deploymentId);
              }
              store.bindDeploymentForStop(
                  deploymentId,
                  result.jobId(),
                  prepared.runtimeEnvironment().runtimeRevision());
              store.event(
                  taskId,
                  deploymentId,
                  "START_CANCEL_PENDING",
                  from.name(),
                  ObservedState.STOPPING.name(),
                  "启动提交已返回，但期间 Execution 停止意图已生效，立即取消该 Flink 任务");
              return true;
            });

    if (Boolean.TRUE.equals(cancelAfterSubmit)) {
      stopBoundJob(
          taskId,
          deploymentId,
          result.jobId(),
          "并发停止请求已取消刚提交的 Flink 任务");
    }
    return store.deploymentView(requireCurrentExecutionRow(taskId, deploymentId));
  }

  void markStartFailure(
      long taskId, long deploymentId, RealtimeEngineException exception) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(taskId);
          DeploymentRow row = requireCurrentExecutionRow(taskId, deploymentId);
          SyncExecution execution = row.execution();
          boolean stopRequested = execution.desiredState() == DesiredState.STOPPED;
          ObservedState target =
              exception.uncertain() ? ObservedState.UNKNOWN : ObservedState.FAILED;
          stateMachine.requireTransition(execution, target);
          store.markDeployFailure(
              taskId,
              deploymentId,
              exception.uncertain(),
              stopRequested,
              exception.getMessage());
          store.event(
              taskId,
              deploymentId,
              exception.uncertain() ? "START_UNCERTAIN" : "START_FAILED",
              execution.observedState().name(),
              target.name(),
              exception.getMessage());
        });
  }

  void stop(long taskId) {
    StopReservation reservation =
        transactions.execute(
            status -> {
              store.lockDefinition(taskId);
              DeploymentRow deployment = store.latestDeployment(taskId).orElse(null);
              if (deployment == null) {
                return new StopReservation(null, true, false);
              }
              SyncExecution execution = deployment.execution();
              if (execution.terminal()) {
                return new StopReservation(deployment, true, false);
              }
              if (execution.desiredState() == DesiredState.STOPPED
                  && execution.observedState() == ObservedState.STOPPING) {
                return new StopReservation(deployment, false, true);
              }

              stateMachine.requireTransition(execution, ObservedState.STOPPING);
              store.markStopping(taskId, deployment.id());
              store.event(
                  taskId,
                  deployment.id(),
                  "STOP_REQUESTED",
                  execution.observedState().name(),
                  ObservedState.STOPPING.name(),
                  "已请求停止当前 SyncExecution");
              return new StopReservation(deployment, false, false);
            });

    if (reservation == null || reservation.settled() || reservation.inProgress()) {
      return;
    }

    DeploymentRow deployment = reservation.deployment();
    if (deployment == null || !StringUtils.hasText(deployment.engineJobId())) {
      return;
    }
    stopBoundJob(taskId, deployment.id(), deployment.engineJobId(), "Flink 已停止任务");
  }

  void stopBoundJob(long taskId, long deploymentId, String jobId, String successMessage) {
    DeploymentRow deployment = requireCurrentExecutionRow(taskId, deploymentId);
    ComputeEnvironmentSnapshot runtimeEnvironment =
        preparation.deploymentRuntime(taskId, deployment);
    try {
      RuntimeStatus runtime = gateway.status(runtimeEnvironment, jobId);
      if (runtime.state() == RuntimeStatus.State.RUNNING) {
        gateway.stop(runtimeEnvironment, jobId);
        waitForRuntimeStop(runtimeEnvironment, jobId);
      } else if (runtime.state() == RuntimeStatus.State.UNKNOWN) {
        throw new RealtimeEngineException("Flink 状态未知，无法确认停止结果", true, null, null);
      }
      markStopped(taskId, deploymentId, jobId, successMessage);
    } catch (RealtimeEngineException exception) {
      markStopUncertain(taskId, deploymentId, jobId, exception.getMessage());
      throw exception;
    }
  }

  DeploymentRow requireCurrentExecutionRow(long taskId, long executionId) {
    DeploymentRow latest =
        store.latestDeployment(taskId)
            .orElseThrow(() -> new IllegalStateException("SyncExecution 不存在：" + executionId));
    if (latest.id() != executionId) {
      throw new IllegalStateException("当前 SyncExecution 已变化，请刷新后重试");
    }
    return latest;
  }

  private void markStopUncertain(
      long taskId, long deploymentId, String jobId, String message) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(taskId);
          DeploymentRow row = requireCurrentExecutionRow(taskId, deploymentId);
          SyncExecution execution = row.execution();
          stateMachine.requireTransition(execution, ObservedState.UNKNOWN);
          store.reconcile(
              taskId,
              deploymentId,
              ObservedState.UNKNOWN.name(),
              ObservedState.UNKNOWN.name(),
              jobId,
              message);
          store.event(
              taskId,
              deploymentId,
              "STOP_UNCERTAIN",
              execution.observedState().name(),
              ObservedState.UNKNOWN.name(),
              message);
        });
  }

  private void markStopped(
      long taskId, long deploymentId, String jobId, String message) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(taskId);
          DeploymentRow row = requireCurrentExecutionRow(taskId, deploymentId);
          SyncExecution execution = row.execution();
          if (execution.desiredState() == DesiredState.STOPPED
              && execution.observedState() == ObservedState.STOPPED) {
            return;
          }
          stateMachine.requireTransition(execution, ObservedState.STOPPED);
          store.reconcile(
              taskId,
              deploymentId,
              ObservedState.STOPPED.name(),
              ObservedState.STOPPED.name(),
              jobId,
              null);
          store.event(
              taskId,
              deploymentId,
              "STOPPED",
              execution.observedState().name(),
              ObservedState.STOPPED.name(),
              message);
        });
  }

  private void waitForRuntimeStop(
      ComputeEnvironmentSnapshot runtimeEnvironment, String jobId) {
    for (int attempt = 0; attempt < STOP_POLL_ATTEMPTS; attempt++) {
      RuntimeStatus status = gateway.status(runtimeEnvironment, jobId);
      if (status.state() == RuntimeStatus.State.TERMINATED
          || status.state() == RuntimeStatus.State.NONE) {
        return;
      }
      if (status.state() == RuntimeStatus.State.UNKNOWN) {
        throw new RealtimeEngineException("Flink 停止状态未知，等待后续对账", true, null, null);
      }
      try {
        Thread.sleep(STOP_POLL_INTERVAL_MILLIS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new RealtimeEngineException("等待 Flink 停止时被中断", true, null, exception);
      }
    }
    throw new RealtimeEngineException("Flink 任务未在 5 秒内停止，等待后续对账", true, null, null);
  }

  private record StopReservation(
      DeploymentRow deployment, boolean settled, boolean inProgress) {}
}
