package io.yak.ops.business.asset.schedule;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.asset.health.HealthRecomputeService;
import io.yak.ops.business.asset.reconcile.AssetReconcileService;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 资产闹钟入口(ticket 98):恢复 Project 上下文后执行对账/健康度重算/概览快照。 */
@Slf4j
@Component(AssetScheduleEngineBridge.HANDLER)
@RequiredArgsConstructor
public class AssetScheduleHandler implements ScheduleHandler {

  private final AssetReconcileService reconcileService;
  private final HealthRecomputeService healthRecompute;
  private final ProjectContextScope projectScope;

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long projectId = context.requiredLong("projectId");
    String task = context.requiredString("task");
    return projectScope.call(
        new ProjectContext(projectId, null),
        () -> switch (task) {
          case AssetScheduleEngineBridge.TASK_RECONCILE -> {
            try {
              String summary = reconcileService.scheduledReconcile(projectId).stream()
                  .map(o -> o.sourceType() + ":" + (o.error() == null ? o.scanned() : "失败"))
                  .collect(Collectors.joining(", "));
              yield ScheduleExecutionResult.accepted(null, "资产对账完成 " + summary);
            } catch (AssetException e) {
              // 忙/无 provider:等下一轮闹钟,失败可见
              yield ScheduleExecutionResult.accepted(null, "资产对账跳过:" + e.getMessage());
            }
          }
          case AssetScheduleEngineBridge.TASK_HEALTH -> {
            var report = healthRecompute.recomputeAll(projectId);
            yield ScheduleExecutionResult.accepted(null,
                "健康度重算 " + report.assets() + " 项,浏览缓存写 " + report.viewRowsWritten()
                    + " 行,清理流水 " + report.purgedViews() + " 行");
          }
          case AssetScheduleEngineBridge.TASK_SNAPSHOT -> {
            int layers = healthRecompute.snapshotDaily(projectId);
            yield ScheduleExecutionResult.accepted(null, "概览快照 " + layers + " 层");
          }
          default -> ScheduleExecutionResult.accepted(null, "未知资产任务:" + task);
        });
  }
}
