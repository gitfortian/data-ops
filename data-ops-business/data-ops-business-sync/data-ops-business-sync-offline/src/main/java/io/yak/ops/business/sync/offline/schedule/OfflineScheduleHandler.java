package io.yak.ops.business.sync.offline.schedule;

import io.yak.ops.common.enums.PublishState;
import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.domain.core.BatchTriggerToken;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.springframework.stereotype.Component;

@ConditionalOnOfflineSyncEnabled
@Component(OfflineScheduleEngineBridge.HANDLER)
public class OfflineScheduleHandler implements ScheduleHandler {

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineScheduleExecutionGateway executionGateway;
  private final OfflineScheduleEngineBridge engine;
  private final OfflineScheduleLifecycle lifecycle;
  private final ProjectContextScope projectScope;

  public OfflineScheduleHandler(
      OfflineJobDefinitionRepository definitionRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineScheduleExecutionGateway executionGateway,
      OfflineScheduleEngineBridge engine,
      OfflineScheduleLifecycle lifecycle,
      ProjectContextScope projectScope) {
    this.definitionRepository = definitionRepository;
    this.scheduleRepository = scheduleRepository;
    this.executionGateway = executionGateway;
    this.engine = engine;
    this.lifecycle = lifecycle;
    this.projectScope = projectScope;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long projectId = context.requiredLong("projectId");
    long definitionId = context.requiredLong("definitionId");
    return projectScope.call(
        new ProjectContext(projectId, null),
        () -> executeInProject(context, projectId, definitionId));
  }

  private ScheduleExecutionResult executeInProject(
      ScheduleExecutionContext context, long projectId, long definitionId) {
    OfflineJobDefinition definition = definitionRepository.findById(definitionId).orElse(null);
    if (definition == null) {
      engine.deleteIfPresent(definitionId);
      return ScheduleExecutionResult.accepted(null, "离线同步任务已删除，清理残留调度计划");
    }
    if (definition.requireProjectId() != projectId) {
      throw new IllegalStateException(
          "Offline Schedule Project 与 Definition Project 不一致：definitionId=" + definitionId);
    }

    OfflineSchedule schedule = scheduleRepository.findSchedule(definitionId);
    if (!PublishState.PUBLISHED.matches(definition.getReleaseState())
        || schedule == null
        || !schedule.enabled()) {
      lifecycle.sync(definitionId);
      return ScheduleExecutionResult.accepted(null, "离线同步任务未启用调度，本次触发忽略");
    }

    if (executionGateway.hasOccupyingBatch(definitionId)) {
      lifecycle.refreshRuntimeState(definitionId, context.actualFireTime());
      return ScheduleExecutionResult.accepted(null, "任务已有运行中的 BatchExecution，本次调度触发跳过");
    }

    try {
      String triggerToken = BatchTriggerToken.schedule(
          context.key().value(), context.scheduledFireTime());
      Long executionId = executionGateway.submitScheduled(definitionId, triggerToken);
      return ScheduleExecutionResult.accepted(
          executionId == null ? null : String.valueOf(executionId),
          "离线同步任务已提交 Link-Up 执行");
    } finally {
      lifecycle.refreshRuntimeState(definitionId, context.actualFireTime());
    }
  }
}
