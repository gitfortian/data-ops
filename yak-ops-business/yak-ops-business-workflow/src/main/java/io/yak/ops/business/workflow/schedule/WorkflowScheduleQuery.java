package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 工作流调度定义查询。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleQuery {
  private final WorkflowScheduleDao dao;
  private final WorkflowJsonCodec json;

  public WorkflowScheduleQuery(WorkflowScheduleDao dao, WorkflowJsonCodec json) {
    this.dao = dao;
    this.json = json;
  }

  public List<WorkflowScheduleVO> list(String workflowId, String status) {
    String state = blank(status) ? null : status.trim().toUpperCase(Locale.ROOT);
    if (state != null && !List.of("ONLINE", "OFFLINE").contains(state)) {
      throw new IllegalArgumentException("不支持的调度状态：" + state);
    }
    return dao.selectSchedules(blank(workflowId) ? null : workflowId.trim(), state)
        .stream().map(this::view).toList();
  }

  public WorkflowScheduleVO get(String id) {
    return view(require(id));
  }

  /** Narrow cross-subsystem read corridor used by Trigger and Backfill orchestration. */
  public WorkflowSchedulePO require(String id) {
    if (blank(id)) throw new IllegalArgumentException("调度 ID 不能为空");
    WorkflowSchedulePO value = dao.selectSchedule(id.trim());
    if (value == null) throw new IllegalArgumentException("工作流调度不存在：" + id);
    return value;
  }

  /** Maps a persisted schedule to its public read model without changing lifecycle state. */
  public WorkflowScheduleVO view(WorkflowSchedulePO value) {
    return new WorkflowScheduleVO(
        value.getId(), value.getWorkflowId(), value.getName(), value.getTriggerType(),
        value.getCronExpression(), value.getTimezone(), value.getStartTime(), value.getEndTime(),
        value.getStatus(), value.getExecutionStrategy(), value.getMisfireStrategy(),
        json.readMap(value.getInputJson()), value.getLastFireTime(), value.getNextFireTime(),
        value.getCreateTime(), value.getUpdateTime());
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
