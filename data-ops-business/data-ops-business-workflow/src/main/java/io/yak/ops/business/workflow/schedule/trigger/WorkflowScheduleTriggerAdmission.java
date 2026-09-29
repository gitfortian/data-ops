package io.yak.ops.business.workflow.schedule.trigger;

import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;

import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Trigger Ledger 的短事务准入、绑定、人工恢复预留和队首推进。实际 Workflow 启动不在这里执行。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerAdmission {
  private static final Set<String> EXECUTION_TERMINAL = Set.of(
      "SUCCESS", "SUCCESS_WITH_WARNINGS", "FAILED", "CANCELED", "TIMED_OUT");
  private static final Set<String> LEDGER_TERMINAL = Set.of(
      "SUCCEEDED", "FAILED", "CANCELED", "SKIPPED");

  private final WorkflowScheduleTriggerDao ledger;
  private final WorkflowScheduleQuery schedules;
  private final WorkflowExecutionDao executions;

  public WorkflowScheduleTriggerAdmission(
      WorkflowScheduleTriggerDao ledger,
      WorkflowScheduleQuery schedules,
      WorkflowExecutionDao executions) {
    this.ledger = ledger;
    this.schedules = schedules;
    this.executions = executions;
  }

  /** Stage 4 兼容入口；策略事实已经固化到 candidate，不再依赖当前 Schedule 配置。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult admitNew(WorkflowSchedulePO schedule, WorkflowScheduleTriggerPO candidate) {
    return admitNew(candidate);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult admitNew(WorkflowScheduleTriggerPO candidate) {
    WorkflowScheduleTriggerPO trigger = ledger.claim(candidate);
    if (!candidate.getId().equals(trigger.getId()) || !"RECEIVED".equals(trigger.getStatus())) {
      return new AdmissionResult(trigger, false, true);
    }
    ledger.lockWorkflow(trigger.getWorkflowId());
    return decideLocked(trigger);
  }

  /** Stage 4 兼容入口。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult readmit(WorkflowSchedulePO schedule, WorkflowScheduleTriggerPO candidate) {
    return readmit(candidate);
  }

  /** 启动恢复时重新处理 RECEIVED/无绑定 LAUNCHING 中间态。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult readmit(WorkflowScheduleTriggerPO candidate) {
    ledger.lockWorkflow(candidate.getWorkflowId());
    WorkflowScheduleTriggerPO trigger = current(candidate);
    if (LEDGER_TERMINAL.contains(trigger.getStatus()) || "RUNNING".equals(trigger.getStatus())) {
      return new AdmissionResult(trigger, false, true);
    }
    trigger.setStatus("RECEIVED");
    trigger.setWorkflowExecutionId(null);
    trigger.setExecutionStatus(null);
    trigger.setMessage("启动恢复重新执行 Trigger 准入");
    trigger.setErrorMessage(null);
    touch(trigger);
    save(trigger);
    return decideLocked(trigger);
  }

  /** 启动完成后绑定业务实例；若实例已同步终态，同时预留下一条 SERIAL_WAIT。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult bindLaunch(WorkflowScheduleTriggerPO candidate, String executionId) {
    WorkflowScheduleTriggerPO trigger = current(candidate);
    ledger.lockWorkflow(trigger.getWorkflowId());
    trigger = current(trigger);
    trigger.setWorkflowExecutionId(executionId);
    WorkflowExecutionPO execution = executionId == null ? null : executions.selectExecution(executionId);
    String status = execution == null ? null : execution.getStatus();
    trigger.setExecutionStatus(status);
    if (isExecutionTerminal(status)) {
      markTerminal(trigger, status, execution.getEndedAt());
      return reserveNextLocked(trigger.getWorkflowId());
    }
    trigger.setStatus("RUNNING");
    trigger.setMessage("WorkflowExecution 已创建");
    touch(trigger);
    save(trigger);
    return new AdmissionResult(trigger, false, false);
  }

  /** 启动失败释放 LAUNCHING 占位，并立即尝试推进等待队列。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult failLaunch(WorkflowScheduleTriggerPO candidate, Throwable error) {
    WorkflowScheduleTriggerPO trigger = current(candidate);
    ledger.lockWorkflow(trigger.getWorkflowId());
    trigger = current(trigger);
    trigger.setStatus("FAILED");
    trigger.setMessage("创建 WorkflowExecution 失败");
    trigger.setErrorMessage(safeMessage(error));
    trigger.setCompletedAt(Instant.now());
    touch(trigger);
    save(trigger);
    return reserveNextLocked(trigger.getWorkflowId());
  }

  /**
   * 对同一个终态 WorkflowExecution 做 retry/continue 前先占住工作流串行槽位。
   *
   * <p>终态提交时 SERIAL_WAIT 可能已经推进了后续 Trigger，因此人工恢复不能直接把旧 Execution
   * 拉回 RUNNING。REACTIVATING 是一个 durable reservation：新 Cron/Backfill 会把它视为启动占位，
   * 应用在 Runtime 真正恢复之前宕机时也能由 recoverPending 根据 Execution 的 durable 状态收敛。</p>
   *
   * @return true 表示本次操作持有 Trigger Ledger 恢复预留；false 表示实例没有 Ledger，或实例本来就非终态。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public boolean reserveReactivation(String executionId, String operation) {
    WorkflowScheduleTriggerPO trigger = ledger.selectByExecutionId(executionId);
    if (trigger == null) return false;

    ledger.lockWorkflow(trigger.getWorkflowId());
    trigger = ledger.selectByExecutionId(executionId);
    if (trigger == null) return false;

    WorkflowExecutionPO execution = executions.selectExecution(executionId);
    if (execution == null) {
      throw new IllegalArgumentException("WorkflowExecution 不存在：" + executionId);
    }
    if (!isExecutionTerminal(execution.getStatus())) {
      return false;
    }

    String strategy = trigger.getExecutionStrategy();
    if (!"PARALLEL".equals(strategy)) {
      long active = ledger.countActiveExecutions(trigger.getWorkflowId());
      long launching = ledger.countLaunchingTriggers(trigger.getWorkflowId());
      long waiting = ledger.countWaitingTriggers(trigger.getWorkflowId());
      if (active > 0L || launching > 0L || waiting > 0L) {
        throw new IllegalStateException(
            "工作流串行槽位已由后续实例占用或排队，不能原地恢复旧 WorkflowExecution；"
                + "请等待队列清空后重试，或使用重新运行创建新实例");
      }
    }

    trigger.setStatus("REACTIVATING");
    trigger.setExecutionStatus(execution.getStatus());
    trigger.setMessage("WorkflowExecution 人工恢复已获得执行准入：" + safeOperation(operation));
    trigger.setErrorMessage(null);
    trigger.setCompletedAt(null);
    touch(trigger);
    save(trigger);
    return true;
  }

  /** Runtime 人工恢复调用返回后，以 durable Execution 状态收敛 Ledger。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult finishReactivation(String executionId) {
    WorkflowScheduleTriggerPO trigger = ledger.selectByExecutionId(executionId);
    if (trigger == null) return AdmissionResult.none();

    ledger.lockWorkflow(trigger.getWorkflowId());
    trigger = ledger.selectByExecutionId(executionId);
    if (trigger == null || !"REACTIVATING".equals(trigger.getStatus())) {
      // 极快工作流可能已在 Runtime 调用内部再次进入终态，并由 AFTER_COMMIT listener 完成 Ledger。
      return AdmissionResult.none();
    }

    WorkflowExecutionPO execution = executions.selectExecution(executionId);
    if (execution == null) {
      trigger.setStatus("FAILED");
      trigger.setExecutionStatus(null);
      trigger.setMessage("人工恢复完成检查时 WorkflowExecution 已不存在");
      trigger.setCompletedAt(Instant.now());
      touch(trigger);
      save(trigger);
      return reserveNextLocked(trigger.getWorkflowId());
    }

    trigger.setExecutionStatus(execution.getStatus());
    if (isExecutionTerminal(execution.getStatus())) {
      markTerminal(trigger, execution.getStatus(), execution.getEndedAt());
      return reserveNextLocked(trigger.getWorkflowId());
    }

    trigger.setStatus("RUNNING");
    trigger.setMessage("WorkflowExecution 已通过人工恢复重新进入运行态");
    trigger.setErrorMessage(null);
    trigger.setCompletedAt(null);
    touch(trigger);
    save(trigger);
    return AdmissionResult.none();
  }

  /** Runtime 人工恢复抛错时释放 REACTIVATING；若 Execution 已恢复成功，则仍以真实状态为准。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult failReactivation(String executionId, Throwable error) {
    WorkflowScheduleTriggerPO trigger = ledger.selectByExecutionId(executionId);
    if (trigger == null) return AdmissionResult.none();

    ledger.lockWorkflow(trigger.getWorkflowId());
    trigger = ledger.selectByExecutionId(executionId);
    if (trigger == null || !"REACTIVATING".equals(trigger.getStatus())) {
      return AdmissionResult.none();
    }

    WorkflowExecutionPO execution = executions.selectExecution(executionId);
    if (execution != null && !isExecutionTerminal(execution.getStatus())) {
      trigger.setStatus("RUNNING");
      trigger.setExecutionStatus(execution.getStatus());
      trigger.setMessage("人工恢复调用抛错，但 WorkflowExecution 已进入活动状态，以 durable 状态为准");
      trigger.setErrorMessage(safeMessage(error));
      trigger.setCompletedAt(null);
      touch(trigger);
      save(trigger);
      return AdmissionResult.none();
    }

    if (execution != null) {
      trigger.setErrorMessage(safeMessage(error));
      markTerminal(trigger, execution.getStatus(), execution.getEndedAt());
    } else {
      trigger.setStatus("FAILED");
      trigger.setExecutionStatus(null);
      trigger.setMessage("人工恢复失败且 WorkflowExecution 已不存在");
      trigger.setErrorMessage(safeMessage(error));
      trigger.setCompletedAt(Instant.now());
      touch(trigger);
      save(trigger);
    }
    return reserveNextLocked(trigger.getWorkflowId());
  }

  /** WorkflowExecution 终态提交后，完成 Ledger 并在同一短事务中预留下一条等待 Trigger。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult completeExecution(String executionId, String executionStatus, Instant endedAt) {
    WorkflowScheduleTriggerPO trigger = ledger.selectByExecutionId(executionId);
    String workflowId = trigger == null
        ? ledger.selectWorkflowIdByExecution(executionId)
        : trigger.getWorkflowId();
    if (workflowId == null || workflowId.isBlank()) return AdmissionResult.none();

    ledger.lockWorkflow(workflowId);
    if (trigger != null) {
      trigger = ledger.selectByExecutionId(executionId);
      if (trigger != null && !LEDGER_TERMINAL.contains(trigger.getStatus())) {
        markTerminal(trigger, executionStatus, endedAt);
      }
    }
    return reserveNextLocked(workflowId);
  }

  /** 恢复已绑定的 RUNNING/LAUNCHING/REACTIVATING Ledger；返回值可能是新预留的队首。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult recoverBound(WorkflowScheduleTriggerPO candidate, String executionId) {
    WorkflowScheduleTriggerPO trigger = current(candidate);
    ledger.lockWorkflow(trigger.getWorkflowId());
    trigger = current(trigger);
    WorkflowExecutionPO execution = executionId == null ? null : executions.selectExecution(executionId);
    if (execution == null) {
      trigger.setStatus("RECEIVED");
      trigger.setWorkflowExecutionId(null);
      trigger.setExecutionStatus(null);
      trigger.setMessage("启动恢复未找到原 WorkflowExecution，等待重新准入");
      touch(trigger);
      save(trigger);
      return new AdmissionResult(trigger, false, false);
    }

    trigger.setWorkflowExecutionId(executionId);
    trigger.setExecutionStatus(execution.getStatus());
    if (isExecutionTerminal(execution.getStatus())) {
      markTerminal(trigger, execution.getStatus(), execution.getEndedAt());
      return reserveNextLocked(trigger.getWorkflowId());
    }
    trigger.setStatus("RUNNING");
    trigger.setMessage("启动恢复已重新绑定 WorkflowExecution");
    trigger.setCompletedAt(null);
    touch(trigger);
    save(trigger);
    return new AdmissionResult(trigger, false, false);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public AdmissionResult reserveNext(String workflowId) {
    ledger.lockWorkflow(workflowId);
    return reserveNextLocked(workflowId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void skip(WorkflowScheduleTriggerPO candidate, String message) {
    WorkflowScheduleTriggerPO trigger = current(candidate);
    if (LEDGER_TERMINAL.contains(trigger.getStatus()) || "RUNNING".equals(trigger.getStatus())) return;
    ledger.lockWorkflow(trigger.getWorkflowId());
    trigger = current(trigger);
    if (LEDGER_TERMINAL.contains(trigger.getStatus()) || "RUNNING".equals(trigger.getStatus())) return;
    markSkipped(trigger, message);
  }

  private AdmissionResult decideLocked(WorkflowScheduleTriggerPO trigger) {
    String strategy = trigger.getExecutionStrategy();
    long active = ledger.countActiveExecutions(trigger.getWorkflowId());
    long launching = ledger.countLaunchingTriggers(trigger.getWorkflowId());
    long waiting = ledger.countWaitingTriggers(trigger.getWorkflowId());
    boolean busy = active > 0L || launching > 0L || waiting > 0L;

    if ("SERIAL_DISCARD".equals(strategy) && busy) {
      markSkipped(trigger, "已有运行、启动、恢复或排队中的 WorkflowExecution，按 SERIAL_DISCARD 跳过");
      return new AdmissionResult(trigger, false, false);
    }
    if ("SERIAL_WAIT".equals(strategy) && busy) {
      trigger.setStatus("WAITING");
      trigger.setMessage("已有运行、启动、恢复或排队中的 WorkflowExecution，进入串行等待队列");
      touch(trigger);
      save(trigger);
      return new AdmissionResult(trigger, false, false);
    }

    reserveLaunch(trigger);
    return new AdmissionResult(trigger, true, false);
  }

  private AdmissionResult reserveNextLocked(String workflowId) {
    if (ledger.countActiveExecutions(workflowId) > 0L
        || ledger.countLaunchingTriggers(workflowId) > 0L) {
      return AdmissionResult.none();
    }
    while (true) {
      WorkflowScheduleTriggerPO waiting = ledger.selectNextWaiting(workflowId);
      if (waiting == null) return AdmissionResult.none();

      if (!isBackfill(waiting)) {
        WorkflowSchedulePO schedule = safeSchedule(waiting.getScheduleId());
        if (schedule == null || !"ONLINE".equals(schedule.getStatus())) {
          markSkipped(waiting, "等待期间调度已停用或删除");
          continue;
        }
      }
      if (!"SERIAL_WAIT".equals(waiting.getExecutionStrategy())) {
        markSkipped(waiting, "等待期间执行策略不是 SERIAL_WAIT，旧 Trigger 不再推进");
        continue;
      }
      reserveLaunch(waiting);
      return new AdmissionResult(waiting, true, false);
    }
  }

  private boolean isBackfill(WorkflowScheduleTriggerPO trigger) {
    return trigger.getBackfillId() != null && !trigger.getBackfillId().isBlank();
  }

  private void reserveLaunch(WorkflowScheduleTriggerPO trigger) {
    trigger.setStatus("LAUNCHING");
    trigger.setMessage("Trigger 已获得执行准入，等待创建 WorkflowExecution");
    trigger.setLaunchedAt(Instant.now());
    trigger.setErrorMessage(null);
    touch(trigger);
    save(trigger);
  }

  private void markSkipped(WorkflowScheduleTriggerPO trigger, String message) {
    trigger.setStatus("SKIPPED");
    trigger.setMessage(message);
    trigger.setCompletedAt(Instant.now());
    touch(trigger);
    save(trigger);
  }

  private void markTerminal(WorkflowScheduleTriggerPO trigger, String executionStatus, Instant endedAt) {
    trigger.setExecutionStatus(executionStatus);
    trigger.setStatus(ledgerStatus(executionStatus));
    trigger.setMessage("WorkflowExecution 已进入终态：" + executionStatus);
    trigger.setCompletedAt(endedAt == null ? Instant.now() : endedAt);
    touch(trigger);
    save(trigger);
  }

  private String ledgerStatus(String executionStatus) {
    if ("SUCCESS".equals(executionStatus) || "SUCCESS_WITH_WARNINGS".equals(executionStatus)) return "SUCCEEDED";
    if ("CANCELED".equals(executionStatus)) return "CANCELED";
    return "FAILED";
  }

  private boolean isExecutionTerminal(String status) {
    return status != null && EXECUTION_TERMINAL.contains(status);
  }

  private WorkflowScheduleTriggerPO current(WorkflowScheduleTriggerPO candidate) {
    WorkflowScheduleTriggerPO current = ledger.selectByDedupeKey(candidate.getDedupeKey());
    if (current == null) throw new IllegalArgumentException("Trigger Ledger 不存在：" + candidate.getId());
    return current;
  }

  private WorkflowSchedulePO safeSchedule(String scheduleId) {
    try {
      return schedules.require(scheduleId);
    } catch (IllegalArgumentException missing) {
      return null;
    }
  }

  private void touch(WorkflowScheduleTriggerPO trigger) {
    trigger.setUpdateTime(Instant.now());
  }

  private void save(WorkflowScheduleTriggerPO trigger) {
    if (ledger.update(trigger) != 1) throw new IllegalStateException("更新 Trigger Ledger 失败：" + trigger.getId());
  }

  private String safeOperation(String operation) {
    if (operation == null || operation.isBlank()) return "MANUAL_REACTIVATION";
    String value = operation.trim();
    return value.length() <= 80 ? value : value.substring(0, 80);
  }

  private String safeMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) current = current.getCause();
    String message = current.getMessage();
    String value = message == null || message.isBlank()
        ? current.getClass().getSimpleName()
        : current.getClass().getSimpleName() + ": " + message;
    return value.length() <= 2000 ? value : value.substring(0, 2000);
  }

  public record AdmissionResult(WorkflowScheduleTriggerPO trigger, boolean launchNow, boolean duplicate) {
    static AdmissionResult none() {
      return new AdmissionResult(null, false, false);
    }
  }
}
