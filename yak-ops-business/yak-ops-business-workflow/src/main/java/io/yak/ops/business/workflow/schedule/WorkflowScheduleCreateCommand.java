package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleCreateDTO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 创建工作流调度定义；创建后默认下线。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleCreateCommand {
  private final WorkflowDefinitionManager definitions;
  private final WorkflowScheduleQuery query;
  private final WorkflowScheduleValidator validator;
  private final WorkflowScheduleDao dao;
  private final WorkflowJsonCodec json;

  public WorkflowScheduleCreateCommand(
      WorkflowDefinitionManager definitions,
      WorkflowScheduleQuery query,
      WorkflowScheduleValidator validator,
      WorkflowScheduleDao dao,
      WorkflowJsonCodec json) {
    this.definitions = definitions;
    this.query = query;
    this.validator = validator;
    this.dao = dao;
    this.json = json;
  }

  public WorkflowScheduleVO create(WorkflowScheduleCreateDTO request) {
    if (request == null || request.workflowId() == null || request.workflowId().isBlank()) {
      throw new IllegalArgumentException("工作流 ID 不能为空");
    }
    String workflowId = request.workflowId().trim();
    definitions.get(workflowId);
    var config = validator.normalize(
        request.name(), request.cronExpression(), request.timezone(), request.startTime(),
        request.endTime(), request.executionStrategy(), request.misfireStrategy(), request.input());
    boolean duplicate = dao.selectSchedules(workflowId, null).stream()
        .anyMatch(item -> config.name().equalsIgnoreCase(item.getName()));
    if (duplicate) throw new IllegalArgumentException("同一工作流下调度名称不能重复：" + config.name());

    Instant now = Instant.now();
    WorkflowSchedulePO value = new WorkflowSchedulePO();
    value.setId("workflow-schedule-" + UUID.randomUUID());
    value.setWorkflowId(workflowId);
    value.setName(config.name());
    value.setTriggerType("CRON");
    value.setCronExpression(config.cronExpression());
    value.setTimezone(config.timezone());
    value.setStartTime(config.startTime());
    value.setEndTime(config.endTime());
    value.setStatus("OFFLINE");
    value.setExecutionStrategy(config.executionStrategy());
    value.setMisfireStrategy(config.misfireStrategy());
    value.setInputJson(json.write(config.input()));
    value.setCreateTime(now);
    value.setUpdateTime(now);
    if (dao.insertSchedule(value) != 1) throw new IllegalStateException("创建工作流调度失败");
    return query.view(value);
  }
}
