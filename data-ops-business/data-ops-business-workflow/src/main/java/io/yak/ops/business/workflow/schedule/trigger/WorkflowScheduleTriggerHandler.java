package io.yak.ops.business.workflow.schedule.trigger;

import io.yak.ops.common.enums.PublishState;
import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleAuditCoordinator;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleLifecycle;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleRuntimeState;
import io.yak.ops.business.workflow.schedule.engine.WorkflowScheduleEngineBridge;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Yak Schedule 工作流 Handler：从 durable payload 恢复 Project 后进入 Trigger Ledger 协调器。 */
@Component(WorkflowScheduleEngineBridge.HANDLER)
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerHandler implements ScheduleHandler {
  private final WorkflowScheduleQuery schedules;
  private final WorkflowDefinitionManager definitions;
  private final WorkflowScheduleTriggerCoordinator coordinator;
  private final WorkflowScheduleAuditCoordinator scheduleAudit;
  private final WorkflowScheduleLifecycle lifecycleFallback;
  private final WorkflowScheduleEngineBridge engine;
  private final WorkflowScheduleRuntimeState runtimeState;
  private final ProjectContextScope projectScope;

  @Autowired
  public WorkflowScheduleTriggerHandler(
      WorkflowScheduleQuery schedules,
      WorkflowDefinitionManager definitions,
      WorkflowScheduleTriggerCoordinator coordinator,
      WorkflowScheduleAuditCoordinator scheduleAudit,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState,
      ProjectContextScope projectScope) {
    this.schedules = schedules;
    this.definitions = definitions;
    this.coordinator = coordinator;
    this.scheduleAudit = scheduleAudit;
    this.lifecycleFallback = null;
    this.engine = engine;
    this.runtimeState = runtimeState;
    this.projectScope = projectScope;
  }

  /** Focused compatibility constructor for tests that do not wire business Audit. */
  public WorkflowScheduleTriggerHandler(
      WorkflowScheduleQuery schedules,
      WorkflowDefinitionManager definitions,
      WorkflowScheduleTriggerCoordinator coordinator,
      WorkflowScheduleLifecycle lifecycle,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState,
      ProjectContextScope projectScope) {
    this.schedules = schedules;
    this.definitions = definitions;
    this.coordinator = coordinator;
    this.scheduleAudit = null;
    this.lifecycleFallback = lifecycle;
    this.engine = engine;
    this.runtimeState = runtimeState;
    this.projectScope = projectScope;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long projectId = context.requiredLong("projectId");
    return projectScope.call(
        new ProjectContext(projectId, null),
        () -> executeInProject(context, projectId));
  }

  private ScheduleExecutionResult executeInProject(
      ScheduleExecutionContext context, long projectId) {
    String scheduleId = context.requiredString("scheduleId");
    WorkflowSchedulePO schedule;
    try {
      schedule = schedules.require(scheduleId);
    } catch (IllegalArgumentException missing) {
      engine.deleteIfPresent(scheduleId);
      return ScheduleExecutionResult.accepted(null, "调度定义已删除或不属于当前 Project，清理残留引擎计划");
    }
    if (!Objects.equals(schedule.getProjectId(), projectId)) {
      throw new IllegalStateException("Workflow Schedule durable Project identity 不一致：" + scheduleId);
    }

    Instant plannedFireTime = context.scheduledFireTime() == null
        ? context.actualFireTime()
        : context.scheduledFireTime();
    Instant actualFireTime = context.actualFireTime();

    if (!"ONLINE".equals(schedule.getStatus())) {
      engine.pauseIfPresent(scheduleId);
      runtimeState.clearNext(scheduleId);
      return ScheduleExecutionResult.accepted(null, "调度已停用，本次触发忽略");
    }

    if (schedule.getStartTime() != null && plannedFireTime.isBefore(schedule.getStartTime())) {
      refreshFireState(schedule, actualFireTime);
      return ScheduleExecutionResult.accepted(null, "尚未进入调度生效时间，本次触发忽略");
    }

    if (schedule.getEndTime() != null && plannedFireTime.isAfter(schedule.getEndTime())) {
      expire(scheduleId, actualFireTime);
      return ScheduleExecutionResult.accepted(null, "调度已超过生效时间并自动停用");
    }

    WorkflowDefinitionVO workflow;
    try {
      workflow = definitions.get(schedule.getWorkflowId());
    } catch (IllegalArgumentException missing) {
      offline(scheduleId, "WORKFLOW_DEFINITION_MISSING");
      return ScheduleExecutionResult.accepted(null, "工作流定义已删除，调度自动停用");
    }
    if (!PublishState.PUBLISHED.matches(workflow.status()) || workflow.activeVersionId() == null) {
      offline(scheduleId, "WORKFLOW_NOT_ONLINE");
      return ScheduleExecutionResult.accepted(null, "工作流已下线，调度自动停用");
    }

    try {
      return coordinator.submit(
          schedule,
          context.triggerId(),
          plannedFireTime,
          actualFireTime,
          context.manual() ? "MANUAL" : "CRON");
    } finally {
      refreshFireState(schedule, actualFireTime);
    }
  }

  private void expire(String scheduleId, Instant actualFireTime) {
    if (scheduleAudit != null) {
      scheduleAudit.expire(scheduleId, actualFireTime);
    } else {
      lifecycleFallback.expire(scheduleId, actualFireTime);
    }
  }

  private void offline(String scheduleId, String cause) {
    if (scheduleAudit != null) {
      scheduleAudit.offlineFromScheduler(scheduleId, cause);
    } else {
      lifecycleFallback.offline(scheduleId);
    }
  }

  private void refreshFireState(WorkflowSchedulePO schedule, Instant actualFireTime) {
    Instant next = engine.snapshot(schedule.getId())
        .map(snapshot -> snapshot.nextFireTime())
        .orElse(null);
    runtimeState.recordFire(schedule.getId(), actualFireTime, next);
  }
}
