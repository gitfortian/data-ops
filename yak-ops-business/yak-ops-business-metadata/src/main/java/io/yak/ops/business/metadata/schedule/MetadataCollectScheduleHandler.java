package io.yak.ops.business.metadata.schedule;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.metadata.harvest.CollectRunService;
import io.yak.ops.business.metadata.harvest.CollectRunService.TriggerOutcome;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.springframework.stereotype.Component;

/**
 * 采集闹钟的入口（ticket 116）。
 *
 * <p><b>第一件事是恢复项目上下文，不是采集</b>：调度线程没有 HTTP 头，{@code CurrentProject} 在那里是空的，
 * 而目录行的 {@code project_id} NOT NULL、任务读写全按项目窄化。不恢复上下文就等于
 * {@code requireProjectId()} 在最深处抛异常，或更糟——写出一批归属不明的行
 * （plan §9 T10；模板 {@code lifecycle/schedule/LifecycleTtlScheduleHandler}）。
 *
 * <p>操作人由 {@code CollectRunService} 自己落成 {@code system} 而不是从上下文里捞：调度线程没有"当前用户"，
 * 而 {@code yak_md_change.changed_by} 是 NOT NULL。
 */
@Component(MetadataScheduleEngineBridge.HANDLER)
public class MetadataCollectScheduleHandler implements ScheduleHandler {

  private final CollectRunService runService;
  private final ProjectContextScope projectScope;

  public MetadataCollectScheduleHandler(
      CollectRunService runService, ProjectContextScope projectScope) {
    this.runService = runService;
    this.projectScope = projectScope;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long projectId = context.requiredLong("projectId");
    long jobId = context.requiredLong("jobId");
    TriggerOutcome outcome =
        projectScope.call(
            new ProjectContext(projectId, null), () -> runService.triggerScheduled(jobId));
    return ScheduleExecutionResult.accepted(
        outcome.runId() == null ? null : String.valueOf(outcome.runId()), outcome.message());
  }
}
