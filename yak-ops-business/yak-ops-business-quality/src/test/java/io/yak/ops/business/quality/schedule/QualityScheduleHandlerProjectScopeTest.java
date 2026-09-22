package io.yak.ops.business.quality.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.execution.QualityExecutionReceipt;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class QualityScheduleHandlerProjectScopeTest {

  @Test
  void schedulePayloadRestoresProjectBeforeReadingAndRunningMonitor() {
    QualityMonitorRepository repository = mock(QualityMonitorRepository.class);
    QualityExecutionManager executionManager = mock(QualityExecutionManager.class);
    QualityScheduleEngineBridge engine = mock(QualityScheduleEngineBridge.class);
    QualityScheduleLifecycle lifecycle = mock(QualityScheduleLifecycle.class);
    RecordingProjectScope projectScope = new RecordingProjectScope();
    when(repository.findMonitor(42L)).thenReturn(Optional.of(monitor()));
    when(repository.findMonitorSettings(42L)).thenReturn(settings());
    when(executionManager.runScheduled(42L))
        .thenReturn(
            new QualityExecutionReceipt(
                "QM-TEST",
                ExecutionStatus.WAITING,
                CheckResult.RUNNING));
    QualityScheduleHandler handler =
        new QualityScheduleHandler(
            repository,
            executionManager,
            engine,
            lifecycle,
            projectScope);

    ScheduleExecutionResult result = handler.execute(context(7L, 42L));

    assertThat(result.accepted()).isTrue();
    assertThat(result.businessExecutionId()).isEqualTo("QM-TEST");
    assertThat(projectScope.context.get().projectId()).isEqualTo(7L);
    verify(repository).findMonitor(42L);
    verify(executionManager).runScheduled(42L);
    verify(lifecycle).refreshRuntimeState(42L);
  }

  @Test
  void invisibleMonitorDoesNotDeleteAnotherProjectsSchedule() {
    QualityMonitorRepository repository = mock(QualityMonitorRepository.class);
    QualityExecutionManager executionManager = mock(QualityExecutionManager.class);
    QualityScheduleEngineBridge engine = mock(QualityScheduleEngineBridge.class);
    QualityScheduleLifecycle lifecycle = mock(QualityScheduleLifecycle.class);
    RecordingProjectScope projectScope = new RecordingProjectScope();
    when(repository.findMonitor(42L)).thenReturn(Optional.empty());
    QualityScheduleHandler handler =
        new QualityScheduleHandler(
            repository,
            executionManager,
            engine,
            lifecycle,
            projectScope);

    ScheduleExecutionResult result = handler.execute(context(8L, 42L));

    assertThat(result.accepted()).isTrue();
    assertThat(projectScope.context.get().projectId()).isEqualTo(8L);
    verify(engine, never()).deleteIfPresent(42L);
    verify(executionManager, never()).runScheduled(42L);
  }

  private ScheduleExecutionContext context(long projectId, long monitorId) {
    Instant now = Instant.now();
    return new ScheduleExecutionContext(
        "trigger-test",
        new ScheduleKey("yak-ops-quality", String.valueOf(monitorId)),
        "test",
        QualityScheduleEngineBridge.HANDLER,
        Map.of("projectId", projectId, "monitorId", monitorId),
        now,
        now,
        false,
        1);
  }

  private Monitor monitor() {
    return new Monitor(
        42L,
        "orders-quality",
        null,
        3L,
        "mysql",
        "demo",
        null,
        "orders",
        null,
        "owner",
        true,
        null,
        null,
        null,
        null,
        null,
        0,
        List.of());
  }

  private MonitorSettings settings() {
    return new MonitorSettings(
        RunMode.SCHEDULE,
        null,
        null,
        null,
        "0 0 0 * * ?",
        null,
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
