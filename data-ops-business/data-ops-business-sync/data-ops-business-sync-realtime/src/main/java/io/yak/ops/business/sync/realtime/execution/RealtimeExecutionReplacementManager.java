package io.yak.ops.business.sync.realtime.execution;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Owns RestartExecution / ApplyPublishedVersion replacement intent and recovery semantics. */
@Component
public class RealtimeExecutionReplacementManager {

  private final RealtimeExecutionPreparation preparation;
  private final RealtimeExecutionReservationManager reservations;
  private final RealtimeExecutionStateManager states;
  private final RealtimeExecutionStarter starter;

  public RealtimeExecutionReplacementManager(
      RealtimeExecutionPreparation preparation,
      RealtimeExecutionReservationManager reservations,
      RealtimeExecutionStateManager states,
      RealtimeExecutionStarter starter) {
    this.preparation = preparation;
    this.reservations = reservations;
    this.states = states;
    this.starter = starter;
  }

  RealtimeJobView.Deployment restartExecution(long taskId, String requestedKey) {
    String key = reservations.normalizeKey(requestedKey);
    Optional<RealtimeJobView.Deployment> existing = reservations.idempotentView(taskId, key);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }

    Optional<DeploymentRow> pending = reservations.pendingReplacement(taskId);
    if (pending.isPresent()) {
      return resumeReplacement(
          taskId, key, pending.orElseThrow(), RealtimeExecutionIntent.RESTART_EXECUTION);
    }

    DeploymentRow currentRow =
        reservations.requireStableRunningExecutionRow(taskId, "重启");
    SyncExecution currentExecution = currentRow.execution();
    long targetVersionId = reservations.requireExecutionVersionId(currentExecution);
    RealtimeExecutionPrepared prepared =
        preparation.prepareVersion(taskId, targetVersionId);
    preparation.validate(prepared);

    DeploymentRow reserved =
        reservations.reserveReplacementStop(
            taskId,
            currentRow.id(),
            targetVersionId,
            key,
            RealtimeExecutionIntent.RESTART_EXECUTION,
            "RESTART_EXECUTION_STOP_REQUESTED",
            "已为同版本 RestartExecution 预留停止当前运行实例");
    return completeReplacement(
        taskId,
        key,
        prepared,
        reserved,
        RealtimeExecutionIntent.RESTART_EXECUTION,
        "当前 SyncExecution 已停止，准备按原 DefinitionVersion 重启");
  }

  RealtimeJobView.Deployment applyPublishedVersion(long taskId, String requestedKey) {
    String key = reservations.normalizeKey(requestedKey);
    Optional<RealtimeJobView.Deployment> existing = reservations.idempotentView(taskId, key);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }

    Optional<DeploymentRow> pending = reservations.pendingReplacement(taskId);
    if (pending.isPresent()) {
      return resumeReplacement(
          taskId, key, pending.orElseThrow(), RealtimeExecutionIntent.APPLY_PUBLISHED_VERSION);
    }

    DeploymentRow currentRow =
        reservations.requireStableRunningExecutionRow(taskId, "应用已发布版本");
    SyncExecution currentExecution = currentRow.execution();
    long currentVersionId = reservations.requireExecutionVersionId(currentExecution);
    PublishedDefinitionRow target = preparation.requirePublishedDefinition(taskId);
    if (currentVersionId == target.id()) {
      throw new IllegalStateException("当前 SyncExecution 已经运行最新已发布 DefinitionVersion，无需应用");
    }

    RealtimeExecutionPrepared prepared = preparation.prepareVersion(taskId, target);
    preparation.validate(prepared);

    DeploymentRow reserved =
        reservations.reserveReplacementStop(
            taskId,
            currentRow.id(),
            target.id(),
            key,
            RealtimeExecutionIntent.APPLY_PUBLISHED_VERSION,
            "APPLY_PUBLISHED_VERSION_STOP_REQUESTED",
            "已为 ApplyPublishedVersion 预留停止当前运行实例");
    return completeReplacement(
        taskId,
        key,
        prepared,
        reserved,
        RealtimeExecutionIntent.APPLY_PUBLISHED_VERSION,
        "当前 SyncExecution 已停止，准备应用已发布 DefinitionVersion");
  }

  private RealtimeJobView.Deployment resumeReplacement(
      long taskId,
      String key,
      DeploymentRow pending,
      RealtimeExecutionIntent intent) {
    if (!Objects.equals(pending.replacementIdempotencyKey(), key)) {
      throw new IllegalStateException("已有版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
    if (!Objects.equals(pending.replacementCommandType(), intent.name())) {
      throw new IllegalStateException("该 Idempotency-Key 已绑定另一种版本替换命令");
    }
    Long targetVersionId = pending.replacementTargetDefinitionVersionId();
    if (targetVersionId == null) {
      throw new IllegalStateException("待恢复的版本替换命令缺少目标 DefinitionVersionId");
    }

    RealtimeExecutionPrepared prepared =
        preparation.prepareVersion(taskId, targetVersionId);
    preparation.validate(prepared);
    return completeReplacement(
        taskId,
        key,
        prepared,
        pending,
        intent,
        intent == RealtimeExecutionIntent.RESTART_EXECUTION
            ? "当前 SyncExecution 已停止，继续按原 DefinitionVersion 重启"
            : "当前 SyncExecution 已停止，继续应用已固定的 Published DefinitionVersion");
  }

  private RealtimeJobView.Deployment completeReplacement(
      long taskId,
      String key,
      RealtimeExecutionPrepared prepared,
      DeploymentRow source,
      RealtimeExecutionIntent intent,
      String stoppedMessage) {
    DeploymentRow latest = states.requireCurrentExecutionRow(taskId, source.id());
    SyncExecution execution = latest.execution();
    ObservedState observed = execution.observedState();

    if (observed == ObservedState.UNKNOWN || observed == ObservedState.CONFLICT) {
      throw new IllegalStateException("版本替换 Execution 状态不确定，请先执行状态对账");
    }
    if (observed == ObservedState.STOPPING) {
      if (!StringUtils.hasText(latest.engineJobId())) {
        throw new IllegalStateException("版本替换 Execution 缺少 EngineExecutionRef，请先执行状态对账");
      }
      states.stopBoundJob(taskId, latest.id(), latest.engineJobId(), stoppedMessage);
    } else if (observed != ObservedState.STOPPED) {
      throw new IllegalStateException("版本替换命令只能从 STOPPING/STOPPED 状态继续，请先执行状态对账");
    }

    reservations.requireLatestExecutionSettled(taskId);
    return starter.startPrepared(taskId, key, prepared, false, intent);
  }
}
