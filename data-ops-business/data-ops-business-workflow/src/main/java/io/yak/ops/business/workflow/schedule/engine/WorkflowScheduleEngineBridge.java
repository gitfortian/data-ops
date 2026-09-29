package io.yak.ops.business.workflow.schedule.engine;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.schedule.YakScheduleGateway;
import io.yak.ops.common.schedule.YakScheduleNamespaces;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Yak Ops Workflow Schedule 到 Yak Framework ScheduleManager 的适配层。
 *
 * <p>业务表是调度定义的事实来源；Yak Schedule 只负责时间触发和引擎路由。Project identity 必须随
 * durable target payload 一起持久化，不能依赖创建计划时的 HTTP ThreadLocal。</p>
 */
@Component
public class WorkflowScheduleEngineBridge {
  static final String NAMESPACE = YakScheduleNamespaces.WORKFLOW;
  public static final String HANDLER = "workflowScheduleHandler";

  private final YakScheduleGateway gateway;

  public WorkflowScheduleEngineBridge(ObjectProvider<ScheduleManager> scheduleManagers) {
    this.gateway = new YakScheduleGateway(scheduleManagers::getIfAvailable, NAMESPACE);
  }

  public boolean available() {
    return gateway.available();
  }

  public ScheduleSnapshot save(WorkflowSchedulePO schedule) {
    return gateway.save(toDefinition(schedule));
  }

  public Optional<ScheduleSnapshot> snapshot(String scheduleId) {
    return gateway.snapshot(name(scheduleId));
  }

  public List<ScheduleSnapshot> list() {
    return gateway.list();
  }

  public void pauseIfPresent(String scheduleId) {
    gateway.pauseIfPresent(name(scheduleId));
  }

  public void deleteIfPresent(String scheduleId) {
    gateway.deleteIfPresent(name(scheduleId));
  }

  public void runNowIfPresent(String scheduleId) {
    gateway.runNowIfPresent(name(scheduleId));
  }

  ScheduleDefinition toDefinition(WorkflowSchedulePO schedule) {
    if (schedule == null) throw new IllegalArgumentException("工作流调度不能为空");
    long projectId = requireProjectId(schedule);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("scheduleId", schedule.getId());
    payload.put("workflowId", schedule.getWorkflowId());
    payload.put("projectId", projectId);

    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("workflowId", schedule.getWorkflowId());
    metadata.put("scheduleId", schedule.getId());
    metadata.put("projectId", String.valueOf(projectId));
    if (schedule.getStartTime() != null) metadata.put("startTime", schedule.getStartTime().toString());
    if (schedule.getEndTime() != null) metadata.put("endTime", schedule.getEndTime().toString());

    return new ScheduleDefinition(
        key(schedule.getId()),
        schedule.getName(),
        ScheduleTrigger.cron(schedule.getCronExpression(), ZoneId.of(schedule.getTimezone())),
        new ScheduleTarget(HANDLER, payload),
        new SchedulePolicy(
            concurrency(schedule.getExecutionStrategy()),
            misfire(schedule.getMisfireStrategy()),
            0),
        "ONLINE".equals(schedule.getStatus()),
        metadata);
  }

  ScheduleKey key(String scheduleId) {
    return gateway.key(name(scheduleId));
  }

  private long requireProjectId(WorkflowSchedulePO schedule) {
    Long value = schedule.getProjectId();
    if (value == null || value <= 0L) {
      throw new IllegalStateException("工作流调度缺少 Project identity：" + schedule.getId());
    }
    return value;
  }

  private String name(String scheduleId) {
    if (scheduleId == null || scheduleId.isBlank()) {
      throw new IllegalArgumentException("调度 ID 不能为空");
    }
    return scheduleId.trim();
  }

  private ConcurrencyPolicy concurrency(String strategy) {
    return "PARALLEL".equals(strategy) ? ConcurrencyPolicy.ALLOW : ConcurrencyPolicy.FORBID;
  }

  private MisfirePolicy misfire(String strategy) {
    return "SKIP".equals(strategy) ? MisfirePolicy.IGNORE : MisfirePolicy.FIRE_ONCE_NOW;
  }
}
