package io.yak.ops.business.quality.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.business.quality.repository.QualityExecutionRepository;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class QualityExecutionDispatcherProjectScopeTest {

  @Test
  void workerRunsInsidePersistedProjectContext() {
    QualityExecutionWorker worker = mock(QualityExecutionWorker.class);
    QualityExecutionRepository executionRepository =
        mock(QualityExecutionRepository.class);
    QualityMonitorRepository monitorRepository =
        mock(QualityMonitorRepository.class);
    ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
    RecordingProjectScope projectScope = new RecordingProjectScope();
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .when(executor)
        .execute(any(Runnable.class));

    QualityExecutionPlan plan = plan(7L);
    QualityExecutionDispatcher dispatcher =
        new QualityExecutionDispatcher(
            worker,
            executionRepository,
            monitorRepository,
            executor,
            projectScope);

    dispatcher.dispatchAfterCommit(plan);

    verify(worker).execute(plan);
    org.assertj.core.api.Assertions.assertThat(projectScope.context.get().projectId())
        .isEqualTo(7L);
  }

  @Test
  void queueRejectionIsRecordedInsidePersistedProjectContext() {
    QualityExecutionWorker worker = mock(QualityExecutionWorker.class);
    QualityExecutionRepository executionRepository =
        mock(QualityExecutionRepository.class);
    QualityMonitorRepository monitorRepository =
        mock(QualityMonitorRepository.class);
    ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
    RecordingProjectScope projectScope = new RecordingProjectScope();
    doThrow(new TaskRejectedException("full"))
        .when(executor)
        .execute(any(Runnable.class));

    QualityExecutionPlan plan = plan(9L);
    QualityExecutionDispatcher dispatcher =
        new QualityExecutionDispatcher(
            worker,
            executionRepository,
            monitorRepository,
            executor,
            projectScope);

    dispatcher.dispatchAfterCommit(plan);

    verify(executionRepository)
        .failExecution(
            org.mockito.ArgumentMatchers.eq(11L),
            org.mockito.ArgumentMatchers.eq("质量执行队列已满"),
            any(java.time.LocalDateTime.class),
            org.mockito.ArgumentMatchers.eq(0L));
    verify(monitorRepository)
        .updateMonitorResult(
            org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.eq("QM-TEST"),
            org.mockito.ArgumentMatchers.eq(CheckResult.ERROR),
            any(java.time.LocalDateTime.class));
    org.assertj.core.api.Assertions.assertThat(projectScope.context.get().projectId())
        .isEqualTo(9L);
  }

  private QualityExecutionPlan plan(long projectId) {
    return new QualityExecutionPlan(
        projectId,
        11L,
        "QM-TEST",
        new MonitorSnapshot(
            42L,
            "orders-quality",
            3L,
            "mysql",
            "demo",
            null,
            "orders",
            null,
            "owner"),
        List.of(),
        RuleFailureAction.CONTINUE,
        false,
        NotifyChannel.MESSAGE,
        null,
        AlertLevel.WARNING);
  }

  private static final class RecordingProjectScope
      implements ProjectContextScope {
    private final AtomicReference<ProjectContext> context =
        new AtomicReference<>();

    @Override
    public <T> T call(ProjectContext project, Supplier<T> action) {
      context.set(project);
      return action.get();
    }
  }
}
