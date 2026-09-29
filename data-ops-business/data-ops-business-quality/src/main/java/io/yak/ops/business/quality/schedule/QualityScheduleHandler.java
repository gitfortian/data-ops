package io.yak.ops.business.quality.schedule;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.springframework.stereotype.Component;

/** Yak Schedule handler that restores Project context before entering Quality execution. */
@ConditionalOnQualityEnabled
@Component(QualityScheduleEngineBridge.HANDLER)
public class QualityScheduleHandler implements ScheduleHandler {
  private final QualityMonitorRepository repository;
  private final QualityExecutionManager executionManager;
  private final QualityScheduleEngineBridge engine;
  private final QualityScheduleLifecycle lifecycle;
  private final ProjectContextScope projectScope;

  public QualityScheduleHandler(
      QualityMonitorRepository repository,
      QualityExecutionManager executionManager,
      QualityScheduleEngineBridge engine,
      QualityScheduleLifecycle lifecycle,
      ProjectContextScope projectScope) {
    this.repository = repository;
    this.executionManager = executionManager;
    this.engine = engine;
    this.lifecycle = lifecycle;
    this.projectScope = projectScope;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long projectId = context.requiredLong("projectId");
    long monitorId = context.requiredLong("monitorId");
    return projectScope.call(
        new ProjectContext(projectId, null),
        () -> executeInProject(monitorId));
  }

  private ScheduleExecutionResult executeInProject(long monitorId) {
    Monitor monitor = repository.findMonitor(monitorId).orElse(null);
    if (monitor == null) {
      return ScheduleExecutionResult.accepted(
          null,
          "质量监控在当前 Project 不可用，本次触发忽略");
    }

    MonitorSettings settings = repository.findMonitorSettings(monitorId);
    if (!monitor.enabled() || settings.runMode() != RunMode.SCHEDULE) {
      engine.pauseIfPresent(monitorId);
      lifecycle.refreshRuntimeState(monitorId);
      return ScheduleExecutionResult.accepted(
          null,
          "质量监控未启用调度，本次触发忽略");
    }

    try {
      var receipt = executionManager.runScheduled(monitorId);
      return ScheduleExecutionResult.accepted(
          receipt.executionNo(),
          "质量检查已提交执行");
    } finally {
      lifecycle.refreshRuntimeState(monitorId);
    }
  }
}
