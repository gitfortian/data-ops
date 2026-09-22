package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleUpdateDTO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 保存工作流调度配置修订；在线计划需先停用，避免数据库与调度引擎配置短暂分叉。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleRevision {
  private final WorkflowScheduleQuery query;
  private final WorkflowScheduleValidator validator;
  private final WorkflowScheduleDao dao;
  private final WorkflowJsonCodec json;

  public WorkflowScheduleRevision(
      WorkflowScheduleQuery query,
      WorkflowScheduleValidator validator,
      WorkflowScheduleDao dao,
      WorkflowJsonCodec json) {
    this.query = query;
    this.validator = validator;
    this.dao = dao;
    this.json = json;
  }

  public WorkflowScheduleVO save(String id, WorkflowScheduleUpdateDTO request) {
    WorkflowSchedulePO value = query.require(id);
    if ("ONLINE".equals(value.getStatus())) {
      throw new IllegalStateException("已启用的调度请先停用后再修改配置");
    }
    var config = validator.normalize(
        request.name(), request.cronExpression(), request.timezone(), request.startTime(),
        request.endTime(), request.executionStrategy(), request.misfireStrategy(), request.input());
    boolean duplicate = dao.selectSchedules(value.getWorkflowId(), null).stream()
        .anyMatch(item -> !value.getId().equals(item.getId())
            && config.name().equalsIgnoreCase(item.getName()));
    if (duplicate) throw new IllegalArgumentException("同一工作流下调度名称不能重复：" + config.name());

    value.setName(config.name());
    value.setCronExpression(config.cronExpression());
    value.setTimezone(config.timezone());
    value.setStartTime(config.startTime());
    value.setEndTime(config.endTime());
    value.setExecutionStrategy(config.executionStrategy());
    value.setMisfireStrategy(config.misfireStrategy());
    value.setInputJson(json.write(config.input()));
    value.setNextFireTime(null);
    value.setUpdateTime(Instant.now());
    if (dao.updateSchedule(value) != 1) throw new IllegalStateException("保存工作流调度失败");
    return query.view(value);
  }
}
