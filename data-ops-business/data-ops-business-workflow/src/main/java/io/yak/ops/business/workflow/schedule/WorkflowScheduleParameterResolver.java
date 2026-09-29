package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 将调度/补数上下文转换为 Workflow input。
 *
 * <p>合并优先级：WorkflowVersion input &lt; Schedule input &lt; Backfill/运维补跑 input &lt; 系统保留参数。</p>
 */
@Component
public class WorkflowScheduleParameterResolver {
  public static final String NAMESPACE = "__schedule";

  private final WorkflowJsonCodec json;

  public WorkflowScheduleParameterResolver(WorkflowJsonCodec json) {
    this.json = json;
  }

  public Map<String, Object> forSchedule(
      WorkflowSchedulePO schedule,
      WorkflowTriggerContext context) {
    if (schedule == null) throw new IllegalArgumentException("调度定义不能为空");
    Map<String, Object> result = new LinkedHashMap<>(json.readMap(schedule.getInputJson()));
    applySystem(
        result,
        context,
        schedule.getTimezone(),
        schedule.getCronExpression(),
        null,
        null,
        null,
        null);
    return Map.copyOf(result);
  }

  public Map<String, Object> forBackfill(
      WorkflowBackfillPO backfill,
      WorkflowTriggerContext context) {
    if (backfill == null) throw new IllegalArgumentException("Backfill 批次不能为空");
    Map<String, Object> result = new LinkedHashMap<>(json.readMap(backfill.getScheduleInputJson()));
    result.putAll(json.readMap(backfill.getInputJson()));
    applySystem(
        result,
        context,
        backfill.getTimezone(),
        backfill.getCronExpression(),
        backfill.getWorkflowVersionId(),
        backfill.getWorkflowVersionNo(),
        backfill.getOperationType(),
        backfill.getSourceExecutionId());
    return Map.copyOf(result);
  }

  private void applySystem(
      Map<String, Object> target,
      WorkflowTriggerContext context,
      String timezone,
      String cronExpression,
      String workflowVersionId,
      Integer workflowVersionNo,
      String operationType,
      String sourceExecutionId) {
    if (context == null || context.plannedFireTime() == null) {
      throw new IllegalArgumentException("调度参数缺少逻辑计划时间");
    }
    ZoneId zone = ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone.trim());
    Instant planned = context.plannedFireTime();
    ZonedDateTime local = planned.atZone(zone);

    Map<String, Object> system = new LinkedHashMap<>();
    system.put("businessDate", local.toLocalDate().toString());
    system.put("scheduleTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local));
    system.put("scheduleTimezone", zone.getId());
    system.put("plannedFireTime", planned.toString());
    system.put("triggerType", context.triggerType().name());
    system.put("triggerId", context.triggerId());
    system.put("scheduleId", context.scheduleId());
    if (context.backfillId() != null) system.put("backfillId", context.backfillId());
    if (cronExpression != null) system.put("cronExpression", cronExpression);
    if (workflowVersionId != null) system.put("workflowVersionId", workflowVersionId);
    if (workflowVersionNo != null) system.put("workflowVersionNo", workflowVersionNo);
    if (operationType != null && !operationType.isBlank()) system.put("operationType", operationType);
    if (sourceExecutionId != null && !sourceExecutionId.isBlank()) {
      system.put("sourceExecutionId", sourceExecutionId);
    }

    // 顶层别名方便 SQL/同步任务直接使用；系统参数始终覆盖用户同名参数。
    system.forEach(target::put);
    target.put(NAMESPACE, Map.copyOf(system));
  }
}
