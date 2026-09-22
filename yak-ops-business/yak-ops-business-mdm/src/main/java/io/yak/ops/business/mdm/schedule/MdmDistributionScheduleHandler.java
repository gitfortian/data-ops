package io.yak.ops.business.mdm.schedule;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.mdm.application.MdmDistributionService;
import io.yak.ops.business.mdm.dao.mapper.MdmDistributionMapper;
import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import io.yak.ops.common.bean.po.mdm.MdmDistributionPO;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 定时分发 handler(R5,review P0-1.8:DAILY/HOURLY 从摆设变真生效)。
 *
 * <p>闹钟只携带 {@code projectId + distributionId},到点后恢复 Project 上下文再复用
 * {@link MdmDistributionService#execute} —— 与手动「执行分发」同一条链路(幂等发布/刷数 +
 * 回写执行结果),定时不是第二套分发实现。行已删除或不再可调度时顺手自清闹钟。
 */
@Slf4j
@Component(MdmDistributionScheduleEngineBridge.HANDLER)
public class MdmDistributionScheduleHandler implements ScheduleHandler {

  private final MdmDistributionMapper distributionMapper;
  private final MdmDistributionService distributionService;
  private final MdmDistributionScheduleEngineBridge engine;
  private final ProjectContextScope projectScope;

  public MdmDistributionScheduleHandler(
      MdmDistributionMapper distributionMapper,
      MdmDistributionService distributionService,
      MdmDistributionScheduleEngineBridge engine,
      ProjectContextScope projectScope) {
    this.distributionMapper = distributionMapper;
    this.distributionService = distributionService;
    this.engine = engine;
    this.projectScope = projectScope;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long projectId = context.requiredLong("projectId");
    long distributionId = context.requiredLong("distributionId");
    return projectScope.call(
        new ProjectContext(projectId, null), () -> fire(distributionId));
  }

  private ScheduleExecutionResult fire(long distributionId) {
    MdmDistributionPO config = distributionMapper.selectById(distributionId);
    if (config == null) {
      engine.deleteIfPresent(distributionId);
      return ScheduleExecutionResult.accepted(
          String.valueOf(distributionId), "分发配置已删除,闹钟自清");
    }
    if (!MdmDistributionScheduleEngineBridge.schedulable(config)) {
      engine.sync(distributionId);
      return ScheduleExecutionResult.accepted(
          String.valueOf(distributionId), "分发配置当前不可调度,本次触发忽略");
    }
    try {
      MdmDistributionService.DistributionResult result =
          distributionService.execute(distributionId, "system");
      return ScheduleExecutionResult.accepted(
          String.valueOf(distributionId),
          "分发已刷新数据服务: " + result.count() + " 条 ACTIVE 记录可服务, path=" + result.apiPath());
    } catch (RuntimeException failure) {
      log.warn("定时分发执行失败 distribution={}", distributionId, failure);
      return new ScheduleExecutionResult(false, String.valueOf(distributionId), failure.getMessage());
    }
  }
}
