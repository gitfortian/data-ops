package io.yak.ops.business.workflow.backfill;

import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao.ProjectBackfillRef;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 启动期按持久化 Project 恢复 RUNNING Backfill 批次物化。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowBackfillReconciler {
  private static final Logger log = LoggerFactory.getLogger(WorkflowBackfillReconciler.class);

  private final WorkflowBackfillDao dao;
  private final WorkflowBackfillPlanner planner;
  private final WorkflowScheduleTriggerCoordinator coordinator;
  private final ProjectContextScope projectScope;

  public WorkflowBackfillReconciler(
      WorkflowBackfillDao dao,
      WorkflowBackfillPlanner planner,
      WorkflowScheduleTriggerCoordinator coordinator,
      ProjectContextScope projectScope) {
    this.dao = dao;
    this.planner = planner;
    this.coordinator = coordinator;
    this.projectScope = projectScope;
  }

  @Order(15)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    for (ProjectBackfillRef candidate : dao.selectRunningForReconciliation()) {
      try {
        projectScope.run(
            new ProjectContext(candidate.projectId(), null),
            () -> reconcileInProject(candidate));
      } catch (RuntimeException exception) {
        log.error(
            "[workflow-backfill] reconcile batch failed projectId={}, backfill={}, message={}",
            candidate.projectId(),
            candidate.backfillId(),
            exception.getMessage(),
            exception);
      }
    }
  }

  private void reconcileInProject(ProjectBackfillRef candidate) {
    WorkflowBackfillPO backfill = dao.select(candidate.backfillId());
    if (backfill == null || !"RUNNING".equals(backfill.getStatus())) return;

    var plan = planner.plan(
        backfill.getCronExpression(),
        backfill.getTimezone(),
        backfill.getStartBusinessDate(),
        backfill.getEndBusinessDate());
    for (var occurrence : plan.occurrences()) {
      try {
        coordinator.submitBackfill(
            backfill.getId(), occurrence.businessDate(), occurrence.scheduleInstant());
      } catch (RuntimeException exception) {
        log.warn(
            "[workflow-backfill] reconcile occurrence failed projectId={}, backfill={}, businessDate={}, message={}",
            candidate.projectId(),
            backfill.getId(),
            occurrence.businessDate(),
            exception.getMessage());
      }
    }
    log.info(
        "[workflow-backfill] reconciled projectId={}, batch={}, occurrences={}",
        candidate.projectId(),
        backfill.getId(),
        plan.occurrences().size());
  }
}
