package io.yak.ops.business.workflow.schedule.trigger;

import io.yak.ops.business.workflow.execution.WorkflowExecutionReactivationGuard;
import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleParameterResolver;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;

import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowScheduleTriggerIdentity;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission.AdmissionResult;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Trigger Ledger、幂等、Backfill、运维补跑与 WorkflowExecution 并发策略统一协调器。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerCoordinator implements WorkflowExecutionReactivationGuard {
  private static final Logger log = LoggerFactory.getLogger(WorkflowScheduleTriggerCoordinator.class);
  private static final String BUSINESS_DATE_RERUN = "BUSINESS_DATE_RERUN";

  private final WorkflowScheduleTriggerDao ledger;
  private final WorkflowScheduleQuery schedules;
  private final WorkflowLauncher launchService;
  private final WorkflowScheduleTriggerAdmission admission;
  private final WorkflowBackfillTriggerGateway backfillGateway;
  private final WorkflowScheduleParameterResolver parameters;

  @Autowired
  public WorkflowScheduleTriggerCoordinator(
      WorkflowScheduleTriggerDao ledger,
      WorkflowScheduleQuery schedules,
      WorkflowLauncher launchService,
      WorkflowScheduleTriggerAdmission admission,
      WorkflowBackfillTriggerGateway backfillGateway,
      WorkflowScheduleParameterResolver parameters) {
    this.ledger = ledger;
    this.schedules = schedules;
    this.launchService = launchService;
    this.admission = admission;
    this.backfillGateway = backfillGateway;
    this.parameters = parameters;
  }

  /** Focused tests retain the lightweight constructor without Backfill wiring. */
  public WorkflowScheduleTriggerCoordinator(
      WorkflowScheduleTriggerDao ledger,
      WorkflowScheduleQuery schedules,
      WorkflowLauncher launchService,
      WorkflowScheduleTriggerAdmission admission) {
    this(ledger, schedules, launchService, admission, null, null);
  }

  public ScheduleExecutionResult submit(
      WorkflowSchedulePO schedule,
      String triggerId,
      Instant plannedFireTime,
      Instant actualFireTime,
      String triggerSource) {
    AdmissionResult result = admission.admitNew(
        schedule,
        newScheduleTrigger(schedule, triggerId, plannedFireTime, actualFireTime, triggerSource));
    return handleAdmission(result, "工作流调度 Trigger 已获得执行准入");
  }

  public ScheduleExecutionResult submitBackfill(
      String backfillId,
      LocalDate businessDate,
      Instant plannedFireTime) {
    WorkflowScheduleTriggerPO candidate =
        requireBackfillGateway().createTrigger(backfillId, businessDate, plannedFireTime);
    AdmissionResult result = admission.admitNew(candidate);
    return handleAdmission(
        result,
        BUSINESS_DATE_RERUN.equals(candidate.getTriggerSource())
            ? "businessDate 运维补跑 Trigger 已获得执行准入"
            : "Backfill Trigger 已获得执行准入");
  }

  private ScheduleExecutionResult handleAdmission(AdmissionResult result, String acceptedMessage) {
    if (result.duplicate()) return duplicateResult(result.trigger());
    if (!result.launchNow()) {
      return ScheduleExecutionResult.accepted(
          result.trigger().getWorkflowExecutionId(), result.trigger().getMessage());
    }
    String executionId = launchReserved(result);
    return ScheduleExecutionResult.accepted(executionId, acceptedMessage);
  }

  public ScheduleExecutionResult recoverMisfire(
      WorkflowSchedulePO schedule,
      Instant plannedFireTime,
      Instant actualFireTime) {
    String triggerId = "workflow-misfire-" + schedule.getId() + "-" + plannedFireTime.toEpochMilli();
    return submit(schedule, triggerId, plannedFireTime, actualFireTime, "MISFIRE_RECOVERY");
  }

  /** 应用重启后修复 Ledger 中间态，并恢复 SERIAL_WAIT / Backfill / 运维补跑队列。 */
  public void recoverPending() {
    List<WorkflowScheduleTriggerPO> pending = ledger.selectPending();
    Set<String> waitingWorkflows = new LinkedHashSet<>();

    for (WorkflowScheduleTriggerPO trigger : pending) {
      String executionId = trigger.getWorkflowExecutionId();
      if ((executionId == null || executionId.isBlank())
          && ("LAUNCHING".equals(trigger.getStatus())
              || "REACTIVATING".equals(trigger.getStatus())
              || "RUNNING".equals(trigger.getStatus()))) {
        executionId = ledger.selectExecutionIdByTrigger(trigger.getTriggerId());
      }

      if (executionId != null && !executionId.isBlank()) {
        AdmissionResult recovered = admission.recoverBound(trigger, executionId);
        if (recovered.launchNow()) {
          launchReserved(recovered);
          continue;
        }
        if (recovered.trigger() != null && "RECEIVED".equals(recovered.trigger().getStatus())) {
          if (!runnable(recovered.trigger())) {
            admission.skip(recovered.trigger(), "原 WorkflowExecution 已不存在且触发来源不可继续，恢复时跳过");
          } else {
            AdmissionResult readmitted = admission.readmit(recovered.trigger());
            if (readmitted.launchNow()) launchReserved(readmitted);
            else if (readmitted.trigger() != null && "WAITING".equals(readmitted.trigger().getStatus())) {
              waitingWorkflows.add(trigger.getWorkflowId());
            }
          }
        }
        continue;
      }

      if (!runnable(trigger)) {
        admission.skip(trigger, "调度/Backfill/运维补跑已不可继续，启动恢复时跳过未启动 Trigger");
        continue;
      }
      if ("WAITING".equals(trigger.getStatus())) {
        waitingWorkflows.add(trigger.getWorkflowId());
        continue;
      }

      AdmissionResult readmitted = admission.readmit(trigger);
      if (readmitted.launchNow()) launchReserved(readmitted);
      else if (readmitted.trigger() != null && "WAITING".equals(readmitted.trigger().getStatus())) {
        waitingWorkflows.add(trigger.getWorkflowId());
      }
    }

    for (String workflowId : waitingWorkflows) {
      launchReserved(admission.reserveNext(workflowId));
    }
  }

  /** WorkflowExecution 终态事务提交后调用，完成 Ledger 并推进 SERIAL_WAIT。 */
  public void completeExecution(String executionId, String executionStatus, Instant endedAt) {
    launchReserved(admission.completeExecution(executionId, executionStatus, endedAt));
  }

  /**
   * 对同一个 Execution 做人工 retry/continue：先持久化 REACTIVATING 占位，再调用 Runtime。
   *
   * <p>如果该实例没有 Trigger Ledger（例如 AdHoc / 草稿测试），直接执行 Runtime 操作；如果它原本就是
   * 非终态，也不需要重新占位。对于 SERIAL_WAIT / SERIAL_DISCARD，终态后如果串行槽位已经交给后续
   * 实例或队列，本次原地恢复会被明确拒绝，不允许悄悄制造并发。</p>
   */
  @Override
  public WorkflowInstanceVO reactivateExecution(
      String executionId,
      String operation,
      Supplier<WorkflowInstanceVO> action) {
    Objects.requireNonNull(action, "workflow reactivation action");
    boolean reserved = admission.reserveReactivation(executionId, operation);
    if (!reserved) return action.get();

    log.info(
        "[workflow-schedule-ledger] reactivation reserved execution={}, operation={}",
        executionId,
        operation);
    try {
      WorkflowInstanceVO result = action.get();
      advanceAfterReactivation(admission.finishReactivation(executionId), executionId, operation);
      return result;
    } catch (RuntimeException exception) {
      AdmissionResult next = admission.failReactivation(executionId, exception);
      try {
        advanceAfterReactivation(next, executionId, operation);
      } catch (RuntimeException queueFailure) {
        exception.addSuppressed(queueFailure);
      }
      throw exception;
    }
  }

  private void advanceAfterReactivation(
      AdmissionResult result,
      String executionId,
      String operation) {
    if (result == null || !result.launchNow()) return;
    try {
      launchReserved(result);
    } catch (RuntimeException exception) {
      // 人工恢复本身已经完成；后续 SERIAL_WAIT 启动失败会由其 Ledger 记录，不反向污染本次 API 结果。
      log.warn(
          "[workflow-schedule-ledger] post-reactivation queue advance failed execution={}, operation={}, message={}",
          executionId,
          operation,
          exception.getMessage());
    }
  }

  private String launchReserved(AdmissionResult initial) {
    AdmissionResult current = initial;
    String firstExecutionId = null;
    RuntimeException firstFailure = null;

    while (current != null && current.launchNow() && current.trigger() != null) {
      WorkflowScheduleTriggerPO trigger = current.trigger();
      if (!runnable(trigger)) {
        admission.skip(trigger, "获得执行准入后调度/Backfill/运维补跑已不可继续，本次 Trigger 跳过");
        current = admission.reserveNext(trigger.getWorkflowId());
        continue;
      }

      try {
        String executionId;
        if (isBackfill(trigger)) {
          WorkflowInstanceVO launched = launchBackfill(trigger);
          executionId = launched.id();
        } else {
          WorkflowDefinitionVO launched = launchSchedule(trigger);
          executionId = launched.latestExecutionId();
        }
        if (executionId == null || executionId.isBlank()) {
          throw new IllegalStateException("工作流启动成功但未返回 WorkflowExecution ID");
        }
        if (firstExecutionId == null) firstExecutionId = executionId;
        log.info(
            "[workflow-schedule-ledger] launched trigger={}, source={}, schedule={}, backfill={}, workflow={}, execution={}, strategy={}, businessDate={}",
            trigger.getId(),
            trigger.getTriggerSource(),
            trigger.getScheduleId(),
            trigger.getBackfillId(),
            trigger.getWorkflowId(),
            executionId,
            trigger.getExecutionStrategy(),
            trigger.getBusinessDate());
        current = admission.bindLaunch(trigger, executionId);
      } catch (RuntimeException exception) {
        if (firstFailure == null) firstFailure = exception;
        log.warn(
            "[workflow-schedule-ledger] launch failed trigger={}, schedule={}, backfill={}, workflow={}, message={}",
            trigger.getId(),
            trigger.getScheduleId(),
            trigger.getBackfillId(),
            trigger.getWorkflowId(),
            exception.getMessage());
        current = admission.failLaunch(trigger, exception);
      }
    }

    if (firstFailure != null) throw firstFailure;
    return firstExecutionId;
  }

  private WorkflowDefinitionVO launchSchedule(WorkflowScheduleTriggerPO trigger) {
    WorkflowSchedulePO schedule = schedules.require(trigger.getScheduleId());
    WorkflowTriggerContext context = WorkflowTriggerContext.scheduled(
        trigger.getTriggerId(),
        schedule.getId(),
        trigger.getPlannedFireTime(),
        schedule.getTimezone());
    Map<String, Object> input = parameters == null ? Map.of() : parameters.forSchedule(schedule, context);
    return launchService.runScheduledPublished(schedule.getWorkflowId(), context, input);
  }

  private WorkflowInstanceVO launchBackfill(WorkflowScheduleTriggerPO trigger) {
    return requireBackfillGateway().launch(trigger);
  }

  private WorkflowScheduleTriggerPO newScheduleTrigger(
      WorkflowSchedulePO schedule,
      String triggerId,
      Instant plannedFireTime,
      Instant actualFireTime,
      String triggerSource) {
    if (schedule == null) throw new IllegalArgumentException("调度定义不能为空");
    if (plannedFireTime == null) throw new IllegalArgumentException("plannedFireTime 不能为空");
    Instant actual = actualFireTime == null ? Instant.now() : actualFireTime;
    Instant now = Instant.now();
    WorkflowScheduleTriggerPO value = new WorkflowScheduleTriggerPO();
    value.setId("workflow-trigger-ledger-" + UUID.randomUUID());
    value.setScheduleId(schedule.getId());
    value.setWorkflowId(schedule.getWorkflowId());
    value.setTriggerId(required(triggerId, "triggerId 不能为空"));
    value.setDedupeKey(WorkflowScheduleTriggerIdentity.scheduled(schedule.getId(), plannedFireTime));
    value.setTriggerSource(triggerSource == null || triggerSource.isBlank() ? "CRON" : triggerSource.trim());
    value.setPlannedFireTime(plannedFireTime);
    value.setActualFireTime(actual);
    value.setBusinessDate(plannedFireTime.atZone(ZoneId.of(schedule.getTimezone())).toLocalDate());
    value.setExecutionStrategy(schedule.getExecutionStrategy());
    value.setMisfireStrategy(schedule.getMisfireStrategy());
    value.setStatus("RECEIVED");
    value.setCreateTime(now);
    value.setUpdateTime(now);
    return value;
  }

  private ScheduleExecutionResult duplicateResult(WorkflowScheduleTriggerPO trigger) {
    return ScheduleExecutionResult.accepted(
        trigger.getWorkflowExecutionId(),
        "重复计划触发已由 Trigger Ledger 幂等拦截，复用状态 " + trigger.getStatus());
  }

  private boolean runnable(WorkflowScheduleTriggerPO trigger) {
    if (isBackfill(trigger)) {
      return backfillGateway != null && backfillGateway.runnable(trigger.getBackfillId());
    }
    WorkflowSchedulePO schedule = safeSchedule(trigger.getScheduleId());
    return schedule != null && "ONLINE".equals(schedule.getStatus());
  }

  private boolean isBackfill(WorkflowScheduleTriggerPO trigger) {
    return trigger.getBackfillId() != null && !trigger.getBackfillId().isBlank();
  }

  private WorkflowSchedulePO safeSchedule(String scheduleId) {
    try {
      return schedules.require(scheduleId);
    } catch (IllegalArgumentException missing) {
      return null;
    }
  }

  private WorkflowBackfillTriggerGateway requireBackfillGateway() {
    if (backfillGateway == null) {
      throw new IllegalStateException("Backfill Trigger Gateway 不可用");
    }
    return backfillGateway;
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
