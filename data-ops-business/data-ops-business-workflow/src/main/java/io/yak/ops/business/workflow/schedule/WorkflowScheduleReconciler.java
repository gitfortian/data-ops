package io.yak.ops.business.workflow.schedule;

import io.yak.ops.common.enums.PublishState;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao.ProjectScheduleRef;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.schedule.engine.WorkflowScheduleEngineBridge;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;
import io.yak.ops.business.workflow.dao.model.WorkflowSchedulePO;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 启动后以 Project-scoped business schedule 为事实来源恢复 Yak Schedule 与 Trigger Ledger。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleReconciler {
  private static final Logger log = LoggerFactory.getLogger(WorkflowScheduleReconciler.class);

  private final WorkflowScheduleDao dao;
  private final WorkflowScheduleTriggerDao triggerDao;
  private final WorkflowDefinitionManager definitions;
  private final WorkflowScheduleEngineBridge engine;
  private final WorkflowScheduleRuntimeState runtimeState;
  private final WorkflowScheduleMisfireRecovery misfireRecovery;
  private final WorkflowScheduleTriggerCoordinator coordinator;
  private final ProjectContextScope projectScope;

  public WorkflowScheduleReconciler(
      WorkflowScheduleDao dao,
      WorkflowScheduleTriggerDao triggerDao,
      WorkflowDefinitionManager definitions,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState,
      WorkflowScheduleMisfireRecovery misfireRecovery,
      WorkflowScheduleTriggerCoordinator coordinator,
      ProjectContextScope projectScope) {
    this.dao = dao;
    this.triggerDao = triggerDao;
    this.definitions = definitions;
    this.engine = engine;
    this.runtimeState = runtimeState;
    this.misfireRecovery = misfireRecovery;
    this.coordinator = coordinator;
    this.projectScope = projectScope;
  }

  @Order(20)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    if (!engine.available()) {
      log.warn("[workflow-schedule] Yak ScheduleManager unavailable, skip startup reconcile");
      return;
    }

    List<ProjectScheduleRef> candidates = dao.selectSchedulesForReconciliation();
    Map<String, ProjectScheduleRef> byId = new HashMap<>();
    candidates.forEach(item -> byId.put(item.scheduleId(), item));

    // Engine schedules with no persisted business owner are stale. Existing OFFLINE schedules are
    // handled inside their Project after scoped reload.
    engine.list().forEach(snapshot -> {
      String scheduleId = snapshot.definition().key().name();
      if (!byId.containsKey(scheduleId)) {
        try {
          engine.deleteIfPresent(scheduleId);
        } catch (RuntimeException exception) {
          log.warn(
              "[workflow-schedule] stale engine schedule cleanup failed schedule={}, message={}",
              scheduleId,
              exception.getMessage());
        }
      }
    });

    Instant now = Instant.now();
    for (ProjectScheduleRef candidate : candidates) {
      try {
        projectScope.run(
            new ProjectContext(candidate.projectId(), null),
            () -> reconcileInProject(candidate, now));
      } catch (RuntimeException exception) {
        log.error(
            "[workflow-schedule] reconcile failed projectId={}, schedule={}, message={}",
            candidate.projectId(),
            candidate.scheduleId(),
            exception.getMessage(),
            exception);
      }
    }

    // Pending Trigger Ledger rows may outlive a Schedule (for Backfill/ops rerun), so discover their
    // Project identities independently instead of deriving recovery only from current schedules.
    for (Long projectId : triggerDao.selectPendingProjectIdsForRecovery()) {
      try {
        projectScope.run(new ProjectContext(projectId, null), coordinator::recoverPending);
      } catch (RuntimeException exception) {
        log.error(
            "[workflow-schedule] trigger ledger recovery failed projectId={}, message={}",
            projectId,
            exception.getMessage(),
            exception);
      }
    }
  }

  private void reconcileInProject(ProjectScheduleRef candidate, Instant now) {
    WorkflowSchedulePO schedule = dao.selectSchedule(candidate.scheduleId());
    if (schedule == null) return;
    if (!"ONLINE".equals(schedule.getStatus())) {
      engine.deleteIfPresent(schedule.getId());
      runtimeState.clearNext(schedule.getId());
      return;
    }

    Instant missedFireTime = null;
    try {
      var workflow = definitions.get(schedule.getWorkflowId());
      boolean workflowReady = PublishState.PUBLISHED.matches(workflow.status()) && workflow.activeVersionId() != null;
      boolean expired = schedule.getEndTime() != null && !schedule.getEndTime().isAfter(now);
      if (!workflowReady || expired) {
        markOffline(schedule, now);
        engine.deleteIfPresent(schedule.getId());
        return;
      }

      Instant persistedNextFireTime = schedule.getNextFireTime();
      missedFireTime = persistedNextFireTime != null && !persistedNextFireTime.isAfter(now)
          ? persistedNextFireTime
          : null;

      var snapshot = engine.save(schedule);
      runtimeState.syncSnapshot(schedule, snapshot);
      log.info(
          "[workflow-schedule] reconciled projectId={}, schedule={}, workflow={}, nextFireTime={}",
          candidate.projectId(),
          schedule.getId(),
          schedule.getWorkflowId(),
          snapshot.nextFireTime());
    } catch (RuntimeException exception) {
      runtimeState.clearNext(schedule.getId());
      throw exception;
    }

    if (missedFireTime != null) {
      try {
        misfireRecovery.recover(schedule, missedFireTime, now);
        log.info(
            "[workflow-schedule] recovered misfire projectId={}, schedule={}, planned={}, policy={}",
            candidate.projectId(),
            schedule.getId(),
            missedFireTime,
            schedule.getMisfireStrategy());
      } catch (RuntimeException exception) {
        log.error(
            "[workflow-schedule] misfire recovery failed projectId={}, schedule={}, planned={}, policy={}, message={}",
            candidate.projectId(),
            schedule.getId(),
            missedFireTime,
            schedule.getMisfireStrategy(),
            exception.getMessage(),
            exception);
      }
    }
  }

  private void markOffline(WorkflowSchedulePO schedule, Instant now) {
    schedule.setStatus("OFFLINE");
    schedule.setNextFireTime(null);
    schedule.setUpdateTime(now);
    if (dao.updateSchedule(schedule) != 1) {
      throw new IllegalStateException("启动对账时停用工作流调度失败：" + schedule.getId());
    }
  }
}
