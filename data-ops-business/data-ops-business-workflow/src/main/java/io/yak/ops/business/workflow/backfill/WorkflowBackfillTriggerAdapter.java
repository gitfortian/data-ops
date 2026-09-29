package io.yak.ops.business.workflow.backfill;

import io.yak.ops.business.workflow.domain.WorkflowScheduleTriggerIdentity;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleParameterResolver;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowBackfillTriggerGateway;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Adapts a durable Backfill batch to the generic Trigger Ledger contract. */
@Component
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowBackfillTriggerAdapter implements WorkflowBackfillTriggerGateway {
  private static final String BUSINESS_DATE_RERUN = "BUSINESS_DATE_RERUN";

  private final WorkflowBackfillQuery backfills;
  private final WorkflowScheduleParameterResolver parameters;
  private final WorkflowLauncher launcher;

  public WorkflowBackfillTriggerAdapter(
      WorkflowBackfillQuery backfills,
      WorkflowScheduleParameterResolver parameters,
      WorkflowLauncher launcher) {
    this.backfills = backfills;
    this.parameters = parameters;
    this.launcher = launcher;
  }

  @Override
  public WorkflowScheduleTriggerPO createTrigger(
      String backfillId,
      LocalDate businessDate,
      Instant plannedFireTime) {
    if (businessDate == null || plannedFireTime == null) {
      throw new IllegalArgumentException("Backfill Trigger 缺少 businessDate / plannedFireTime");
    }
    WorkflowBackfillPO backfill = backfills.require(required(backfillId, "Backfill ID 不能为空"));
    Instant now = Instant.now();
    boolean operational = operational(backfill);

    WorkflowScheduleTriggerPO value = new WorkflowScheduleTriggerPO();
    value.setId("workflow-trigger-ledger-" + UUID.randomUUID());
    value.setScheduleId(backfill.getScheduleId());
    value.setWorkflowId(backfill.getWorkflowId());
    value.setBackfillId(backfill.getId());
    value.setTriggerId((operational ? "workflow-rerun-" : "workflow-backfill-")
        + backfill.getId() + "-" + plannedFireTime.toEpochMilli());
    value.setDedupeKey(WorkflowScheduleTriggerIdentity.backfill(
        backfill.getScheduleId(), backfill.getId(), plannedFireTime));
    value.setTriggerSource(operational ? BUSINESS_DATE_RERUN : "BACKFILL");
    value.setPlannedFireTime(plannedFireTime);
    value.setActualFireTime(now);
    value.setBusinessDate(businessDate);
    value.setExecutionStrategy(backfill.getExecutionStrategy());
    value.setMisfireStrategy("FIRE_ONCE");
    value.setStatus("RECEIVED");
    value.setCreateTime(now);
    value.setUpdateTime(now);
    return value;
  }

  @Override
  public boolean runnable(String backfillId) {
    try {
      WorkflowBackfillPO backfill = backfills.require(backfillId);
      return !"CANCELED".equals(backfill.getStatus());
    } catch (IllegalArgumentException missing) {
      return false;
    }
  }

  @Override
  public WorkflowInstanceVO launch(WorkflowScheduleTriggerPO trigger) {
    if (trigger == null || trigger.getBackfillId() == null || trigger.getBackfillId().isBlank()) {
      throw new IllegalArgumentException("Backfill Trigger 缺少 backfillId");
    }
    WorkflowBackfillPO backfill = backfills.require(trigger.getBackfillId());
    boolean operational = operational(backfill);
    WorkflowTriggerContext context = operational
        ? WorkflowTriggerContext.rerun(
            trigger.getTriggerId(),
            backfill.getScheduleId(),
            backfill.getId(),
            trigger.getPlannedFireTime(),
            backfill.getTimezone())
        : WorkflowTriggerContext.backfill(
            trigger.getTriggerId(),
            backfill.getScheduleId(),
            backfill.getId(),
            trigger.getPlannedFireTime(),
            backfill.getTimezone());
    Map<String, Object> input = parameters.forBackfill(backfill, context);
    return operational
        ? launcher.runOperationalPublished(
            backfill.getWorkflowId(), backfill.getWorkflowVersionId(), context, input)
        : launcher.runBackfillPublished(
            backfill.getWorkflowId(), backfill.getWorkflowVersionId(), context, input);
  }

  private boolean operational(WorkflowBackfillPO backfill) {
    return backfill != null && BUSINESS_DATE_RERUN.equals(backfill.getOperationType());
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
