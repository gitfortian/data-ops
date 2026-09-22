package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;

import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowScheduleTriggerIdentity;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于业务表中持久化 nextFireTime 的启动期 Misfire 恢复。
 *
 * <p>FIRE_ONCE 合并为一次补触发；SKIP 也写入 Ledger，保证错过的计划可审计。</p>
 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleMisfireRecovery {
  private final WorkflowScheduleTriggerCoordinator coordinator;
  private final WorkflowScheduleTriggerDao ledger;

  public WorkflowScheduleMisfireRecovery(
      WorkflowScheduleTriggerCoordinator coordinator,
      WorkflowScheduleTriggerDao ledger) {
    this.coordinator = coordinator;
    this.ledger = ledger;
  }

  public void recover(WorkflowSchedulePO schedule, Instant missedFireTime, Instant recoveredAt) {
    if (schedule == null || missedFireTime == null) return;
    if ("SKIP".equals(schedule.getMisfireStrategy())) {
      recordSkipped(schedule, missedFireTime, recoveredAt);
      return;
    }
    coordinator.recoverMisfire(schedule, missedFireTime, recoveredAt);
  }

  public void recordSkipped(
      WorkflowSchedulePO schedule,
      Instant missedFireTime,
      Instant recoveredAt) {
    Instant now = recoveredAt == null ? Instant.now() : recoveredAt;
    WorkflowScheduleTriggerPO value = new WorkflowScheduleTriggerPO();
    value.setId("workflow-trigger-ledger-" + UUID.randomUUID());
    value.setScheduleId(schedule.getId());
    value.setWorkflowId(schedule.getWorkflowId());
    value.setTriggerId(
        "workflow-misfire-skip-" + schedule.getId() + "-" + missedFireTime.toEpochMilli());
    value.setDedupeKey(WorkflowScheduleTriggerIdentity.scheduled(schedule.getId(), missedFireTime));
    value.setTriggerSource("MISFIRE_RECOVERY");
    value.setPlannedFireTime(missedFireTime);
    value.setActualFireTime(now);
    value.setBusinessDate(
        missedFireTime.atZone(ZoneId.of(schedule.getTimezone())).toLocalDate());
    value.setExecutionStrategy(schedule.getExecutionStrategy());
    value.setMisfireStrategy(schedule.getMisfireStrategy());
    value.setStatus("SKIPPED");
    value.setMessage("服务恢复时发现错过计划，按 SKIP 策略跳过");
    value.setCompletedAt(now);
    value.setCreateTime(now);
    value.setUpdateTime(now);
    ledger.claim(value);
  }
}
