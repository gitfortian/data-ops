package io.yak.ops.business.lifecycle.schedule;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService;
import io.yak.ops.business.lifecycle.stats.StorageSnapshotService;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.springframework.stereotype.Component;

/** 生命周期闹钟入口:恢复 Project 上下文后执行重试/快照采集。 */
@Component(LifecycleScheduleEngineBridge.HANDLER)
public class LifecycleTtlScheduleHandler implements ScheduleHandler {

  private final TtlDispatchService dispatchService;
  private final StorageSnapshotService snapshotService;
  private final ProjectContextScope projectScope;

  public LifecycleTtlScheduleHandler(
      TtlDispatchService dispatchService,
      StorageSnapshotService snapshotService,
      ProjectContextScope projectScope) {
    this.dispatchService = dispatchService;
    this.snapshotService = snapshotService;
    this.projectScope = projectScope;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long projectId = context.requiredLong("projectId");
    String task = context.requiredString("task");
    return projectScope.call(
        new ProjectContext(projectId, null),
        () -> switch (task) {
          case LifecycleScheduleEngineBridge.TASK_RETRY -> {
            int retried = dispatchService.retryDue();
            yield ScheduleExecutionResult.accepted(null, "TTL 重试闹钟处理 " + retried + " 条");
          }
          case LifecycleScheduleEngineBridge.TASK_SNAPSHOT -> {
            var result = snapshotService.collectDaily("system");
            yield ScheduleExecutionResult.accepted(null,
                "存储快照采集 " + result.layers() + " 库 / " + result.tables() + " 表");
          }
          default -> ScheduleExecutionResult.accepted(null, "未知生命周期任务:" + task);
        });
  }
}
