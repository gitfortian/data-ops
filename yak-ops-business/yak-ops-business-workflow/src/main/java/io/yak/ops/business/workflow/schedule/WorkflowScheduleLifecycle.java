package io.yak.ops.business.workflow.schedule;

import io.yak.ops.common.enums.PublishState;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.schedule.engine.WorkflowScheduleEngineBridge;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowSchedulePendingTriggerCancellation;

import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 工作流调度定义启停生命周期，并同步 Yak Schedule 引擎与 Trigger Ledger 状态。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleLifecycle {
  private final WorkflowDefinitionManager definitions;
  private final WorkflowScheduleQuery query;
  private final WorkflowScheduleDao dao;
  private final WorkflowScheduleEngineBridge engine;
  private final WorkflowScheduleRuntimeState runtimeState;
  private final WorkflowSchedulePendingTriggerCancellation pendingCancellation;

  public WorkflowScheduleLifecycle(
      WorkflowDefinitionManager definitions,
      WorkflowScheduleQuery query,
      WorkflowScheduleDao dao,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState,
      WorkflowSchedulePendingTriggerCancellation pendingCancellation) {
    this.definitions = definitions;
    this.query = query;
    this.dao = dao;
    this.engine = engine;
    this.runtimeState = runtimeState;
    this.pendingCancellation = pendingCancellation;
  }

  public WorkflowScheduleVO online(String id) {
    WorkflowSchedulePO value = query.require(id);
    var workflow = definitions.get(value.getWorkflowId());
    if (!PublishState.PUBLISHED.matches(workflow.status()) || workflow.activeVersionId() == null) {
      throw new IllegalStateException("工作流需要先发布并上线，才能启用调度");
    }
    Instant now = Instant.now();
    if (value.getEndTime() != null && !value.getEndTime().isAfter(now)) {
      throw new IllegalStateException("调度生效结束时间已过，请调整生效区间后再启用");
    }

    value.setStatus("ONLINE");
    value.setUpdateTime(now);
    try {
      ScheduleSnapshot snapshot = engine.save(value);
      runtimeState.applySnapshot(value, snapshot);
      save(value);
      return query.view(value);
    } catch (RuntimeException exception) {
      value.setStatus("OFFLINE");
      try {
        engine.pauseIfPresent(value.getId());
      } catch (RuntimeException ignored) {
        // 保留原始注册异常，启动 reconcile 会再次清理残留计划。
      }
      throw exception;
    }
  }

  public WorkflowScheduleVO offline(String id) {
    WorkflowSchedulePO value = query.require(id);
    engine.pauseIfPresent(value.getId());
    value.setStatus("OFFLINE");
    value.setNextFireTime(null);
    value.setUpdateTime(Instant.now());
    save(value);
    pendingCancellation.cancel(
        value.getId(), value.getWorkflowId(), "调度已停用，取消尚未启动的等待 Trigger");
    return query.view(value);
  }

  /** 生效区间结束后由 Schedule Handler 自动停用。 */
  public WorkflowScheduleVO expire(String id, Instant fireTime) {
    WorkflowSchedulePO value = query.require(id);
    engine.pauseIfPresent(value.getId());
    value.setStatus("OFFLINE");
    value.setLastFireTime(fireTime);
    value.setNextFireTime(null);
    value.setUpdateTime(Instant.now());
    save(value);
    pendingCancellation.cancel(
        value.getId(), value.getWorkflowId(), "调度已超过生效时间，取消尚未启动的等待 Trigger");
    return query.view(value);
  }

  public void remove(String id) {
    WorkflowSchedulePO value = query.require(id);
    if ("ONLINE".equals(value.getStatus())) {
      throw new IllegalStateException("已启用的调度请先下线后再删除");
    }
    pendingCancellation.cancel(
        value.getId(), value.getWorkflowId(), "调度定义已删除，取消尚未启动的等待 Trigger");
    engine.deleteIfPresent(value.getId());
    if (dao.deleteSchedule(value.getId()) != 1) {
      throw new IllegalStateException("删除工作流调度失败：" + value.getId());
    }
  }

  private void save(WorkflowSchedulePO value) {
    if (dao.updateSchedule(value) != 1) throw new IllegalStateException("保存工作流调度失败");
  }
}
