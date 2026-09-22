package io.yak.ops.business.quality.execution;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.repository.QualityExecutionRepository;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Dispatches an accepted execution after commit and restores its persisted Project context. */
@Component
@ConditionalOnQualityEnabled
public class QualityExecutionDispatcher {
  private final QualityExecutionWorker worker;
  private final QualityExecutionRepository executionRepository;
  private final QualityMonitorRepository monitorRepository;
  private final ThreadPoolTaskExecutor taskExecutor;
  private final ProjectContextScope projectScope;

  public QualityExecutionDispatcher(
      QualityExecutionWorker worker,
      QualityExecutionRepository executionRepository,
      QualityMonitorRepository monitorRepository,
      @Qualifier("qualityExecutionTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
      ProjectContextScope projectScope) {
    this.worker = worker;
    this.executionRepository = executionRepository;
    this.monitorRepository = monitorRepository;
    this.taskExecutor = taskExecutor;
    this.projectScope = projectScope;
  }

  public void dispatchAfterCommit(QualityExecutionPlan plan) {
    Runnable dispatch = () -> dispatch(plan);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      dispatch.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            dispatch.run();
          }
        });
  }

  private void dispatch(QualityExecutionPlan plan) {
    ProjectContext project = new ProjectContext(plan.projectId(), null);
    try {
      taskExecutor.execute(
          () -> projectScope.run(project, () -> worker.execute(plan)));
    } catch (TaskRejectedException exception) {
      projectScope.run(project, () -> recordQueueRejection(plan));
    }
  }

  private void recordQueueRejection(QualityExecutionPlan plan) {
    LocalDateTime now = LocalDateTime.now();
    executionRepository.failExecution(
        plan.executionId(),
        "质量执行队列已满",
        now,
        0L);
    monitorRepository.updateMonitorResult(
        plan.monitor().id(),
        plan.executionNo(),
        CheckResult.ERROR,
        now);
  }
}
