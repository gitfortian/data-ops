package io.yak.ops.business.workflow.backfill;

import io.yak.ops.common.enums.PublishState;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleParameterResolver;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;

import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillPlanner.Occurrence;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillPlanner.Plan;
import io.yak.ops.common.bean.dto.workflow.WorkflowBackfillCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowBusinessDateRerunDTO;
import io.yak.ops.business.workflow.dao.model.WorkflowBackfillPO;
import io.yak.ops.business.workflow.dao.model.WorkflowSchedulePO;
import io.yak.ops.business.workflow.dao.model.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillPreviewVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Backfill 批次创建、运维补跑、预览、取消与 Trigger Ledger 分发。 */
@Service
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowBackfillManager {
  private static final Logger log = LoggerFactory.getLogger(WorkflowBackfillManager.class);
  private static final Set<String> STRATEGIES = Set.of("SERIAL_WAIT", "PARALLEL");
  private static final Set<String> TERMINAL = Set.of(
      "SUCCESS", "SUCCESS_WITH_WARNINGS", "FAILED", "WARNING", "CANCELED", "TIMED_OUT");
  private static final Set<String> SYSTEM_INPUT_KEYS = Set.of(
      "businessDate",
      "scheduleTime",
      "scheduleTimezone",
      "plannedFireTime",
      "triggerType",
      "triggerId",
      "scheduleId",
      "backfillId",
      "cronExpression",
      "workflowVersionId",
      "workflowVersionNo",
      "operationType",
      "sourceExecutionId",
      WorkflowScheduleParameterResolver.NAMESPACE);

  private final WorkflowScheduleQuery schedules;
  private final WorkflowDefinitionManager definitions;
  private final WorkflowBackfillPlanner planner;
  private final WorkflowBackfillDao dao;
  private final WorkflowBackfillQuery query;
  private final WorkflowScheduleTriggerDao triggers;
  private final WorkflowScheduleTriggerAdmission admission;
  private final WorkflowScheduleTriggerCoordinator coordinator;
  private final WorkflowJsonCodec json;

  public WorkflowBackfillManager(
      WorkflowScheduleQuery schedules,
      WorkflowDefinitionManager definitions,
      WorkflowBackfillPlanner planner,
      WorkflowBackfillDao dao,
      WorkflowBackfillQuery query,
      WorkflowScheduleTriggerDao triggers,
      WorkflowScheduleTriggerAdmission admission,
      WorkflowScheduleTriggerCoordinator coordinator,
      WorkflowJsonCodec json) {
    this.schedules = schedules;
    this.definitions = definitions;
    this.planner = planner;
    this.dao = dao;
    this.query = query;
    this.triggers = triggers;
    this.admission = admission;
    this.coordinator = coordinator;
    this.json = json;
  }

  public WorkflowBackfillPreviewVO preview(WorkflowBackfillCreateDTO request) {
    if (request == null) throw new IllegalArgumentException("Backfill 参数不能为空");
    WorkflowSchedulePO schedule = schedules.require(request.scheduleId());
    Plan plan = planner.plan(
        schedule.getCronExpression(),
        schedule.getTimezone(),
        request.startBusinessDate(),
        request.endBusinessDate());
    List<WorkflowBackfillPreviewVO.OccurrenceVO> visible = plan.occurrences().stream()
        .limit(100)
        .map(value -> new WorkflowBackfillPreviewVO.OccurrenceVO(
            value.businessDate(), value.scheduleInstant(), value.scheduleTime()))
        .toList();
    return new WorkflowBackfillPreviewVO(
        schedule.getId(),
        plan.cronExpression(),
        plan.timezone(),
        request.startBusinessDate(),
        request.endBusinessDate(),
        plan.occurrences().size(),
        plan.occurrences().size() > visible.size(),
        visible);
  }

  public WorkflowBackfillVO create(WorkflowBackfillCreateDTO request) {
    if (request == null) throw new IllegalArgumentException("Backfill 参数不能为空");
    WorkflowSchedulePO schedule = schedules.require(request.scheduleId());
    var workflow = definitions.get(schedule.getWorkflowId());
    if (!PublishState.PUBLISHED.matches(workflow.status()) || workflow.activeVersionId() == null) {
      throw new IllegalStateException("工作流需要先发布并上线，才能执行 Backfill");
    }

    String executionStrategy = strategy(request.executionStrategy());
    Plan plan = planner.plan(
        schedule.getCronExpression(),
        schedule.getTimezone(),
        request.startBusinessDate(),
        request.endBusinessDate());

    WorkflowBackfillPO backfill = base(
        schedule.getWorkflowId(),
        workflow.activeVersionId(),
        workflow.activeVersionNo(),
        schedule.getId(),
        schedule.getName(),
        name(request, schedule),
        "BACKFILL",
        null,
        request.startBusinessDate(),
        request.endBusinessDate(),
        plan,
        executionStrategy,
        schedule.getInputJson(),
        json.write(request.input()));
    persistAndDispatch(backfill, plan.occurrences());
    return query.get(backfill.getId());
  }

  /**
   * 按来源实例的不可变发布版本与调度语义，对指定 businessDate 创建运维补跑。
   * 旧实例的系统调度参数和旧运维血缘会被剥离，再根据新的逻辑计划时间重新注入。
   */
  public WorkflowBackfillVO createBusinessDateRerun(
      String sourceExecutionId,
      WorkflowInstanceVO source,
      WorkflowBusinessDateRerunDTO request) {
    if (source == null || request == null) throw new IllegalArgumentException("指定日期补跑参数不能为空");
    if (!source.id().equals(sourceExecutionId)) throw new IllegalArgumentException("来源实例 ID 不匹配");
    if (!TERMINAL.contains(source.status())) throw new IllegalStateException("仅终态实例支持指定 businessDate 重跑");
    if (source.workflowVersionId() == null || source.workflowVersionNo() == null) {
      throw new IllegalStateException("来源实例没有不可变发布版本，不能执行 businessDate 重跑");
    }

    Map<String, Object> sourceInput = source.input() == null ? Map.of() : source.input();
    String scheduleId = text(sourceInput.get("scheduleId"));
    String cron = text(sourceInput.get("cronExpression"));
    String timezone = text(sourceInput.get("scheduleTimezone"));
    if (scheduleId == null || cron == null || timezone == null) {
      throw new IllegalStateException("来源实例缺少 scheduleId / cronExpression / scheduleTimezone 调度血缘");
    }

    String workflowId = triggers.selectWorkflowIdByExecution(sourceExecutionId);
    if (workflowId == null || workflowId.isBlank()) {
      throw new IllegalStateException("无法从来源实例解析工作流 ID：" + sourceExecutionId);
    }

    Plan plan = planner.plan(cron, timezone, request.businessDate(), request.businessDate());
    Map<String, Object> inheritedInput = userInput(sourceInput);
    String scheduleName = scheduleName(scheduleId);
    String executionStrategy = strategy(request.executionStrategy());
    String batchName = scheduleName + " · 运维补跑 " + request.businessDate();

    WorkflowBackfillPO backfill = base(
        workflowId,
        source.workflowVersionId(),
        source.workflowVersionNo(),
        scheduleId,
        scheduleName,
        batchName,
        "BUSINESS_DATE_RERUN",
        sourceExecutionId,
        request.businessDate(),
        request.businessDate(),
        plan,
        executionStrategy,
        json.write(inheritedInput),
        json.write(request.input()));
    persistAndDispatch(backfill, plan.occurrences());
    return query.get(backfill.getId());
  }

  /** 取消只阻止尚未启动的补数 Trigger，已经 RUNNING 的 WorkflowExecution 继续完成。 */
  public WorkflowBackfillVO cancel(String id) {
    WorkflowBackfillPO backfill = query.require(id);
    if (!"CANCELED".equals(backfill.getStatus())) {
      backfill.setStatus("CANCELED");
      backfill.setUpdateTime(Instant.now());
      if (dao.update(backfill) != 1) throw new IllegalStateException("取消 Backfill 批次失败：" + id);
    }
    for (WorkflowScheduleTriggerPO trigger : triggers.selectByBackfillId(backfill.getId())) {
      if ("RECEIVED".equals(trigger.getStatus()) || "WAITING".equals(trigger.getStatus())) {
        admission.skip(trigger, "Backfill/运维补跑批次已取消，尚未启动的 Trigger 跳过");
      }
    }
    return query.get(backfill.getId());
  }

  private WorkflowBackfillPO base(
      String workflowId,
      String workflowVersionId,
      Integer workflowVersionNo,
      String scheduleId,
      String scheduleName,
      String name,
      String operationType,
      String sourceExecutionId,
      java.time.LocalDate startBusinessDate,
      java.time.LocalDate endBusinessDate,
      Plan plan,
      String executionStrategy,
      String scheduleInputJson,
      String inputJson) {
    Instant now = Instant.now();
    WorkflowBackfillPO backfill = new WorkflowBackfillPO();
    backfill.setId("workflow-backfill-" + UUID.randomUUID());
    backfill.setWorkflowId(workflowId);
    backfill.setWorkflowVersionId(workflowVersionId);
    backfill.setWorkflowVersionNo(workflowVersionNo);
    backfill.setScheduleId(scheduleId);
    backfill.setScheduleName(scheduleName);
    backfill.setName(name);
    backfill.setStatus("RUNNING");
    backfill.setOperationType(operationType);
    backfill.setSourceExecutionId(sourceExecutionId);
    backfill.setStartBusinessDate(startBusinessDate);
    backfill.setEndBusinessDate(endBusinessDate);
    backfill.setCronExpression(plan.cronExpression());
    backfill.setTimezone(plan.timezone());
    backfill.setExecutionStrategy(executionStrategy);
    backfill.setScheduleInputJson(scheduleInputJson);
    backfill.setInputJson(inputJson);
    backfill.setTotalCount(plan.occurrences().size());
    backfill.setCreateTime(now);
    backfill.setUpdateTime(now);
    return backfill;
  }

  private void persistAndDispatch(WorkflowBackfillPO backfill, List<Occurrence> occurrences) {
    if (dao.insert(backfill) != 1) throw new IllegalStateException("创建 Backfill/补跑批次失败");
    for (Occurrence occurrence : occurrences) {
      try {
        coordinator.submitBackfill(
            backfill.getId(), occurrence.businessDate(), occurrence.scheduleInstant());
      } catch (RuntimeException exception) {
        log.warn(
            "[workflow-backfill] dispatch failed batch={}, operation={}, businessDate={}, scheduleTime={}, message={}",
            backfill.getId(),
            backfill.getOperationType(),
            occurrence.businessDate(),
            occurrence.scheduleTime(),
            exception.getMessage());
      }
    }
  }

  private Map<String, Object> userInput(Map<String, Object> sourceInput) {
    Map<String, Object> result = new LinkedHashMap<>();
    sourceInput.forEach((key, value) -> {
      if (!SYSTEM_INPUT_KEYS.contains(key)) result.put(key, value);
    });
    return Map.copyOf(result);
  }

  private String scheduleName(String scheduleId) {
    try {
      WorkflowSchedulePO schedule = schedules.require(scheduleId);
      return schedule.getName();
    } catch (IllegalArgumentException missing) {
      return scheduleId;
    }
  }

  private String text(Object value) {
    if (value == null) return null;
    String result = String.valueOf(value).trim();
    return result.isEmpty() ? null : result;
  }

  private String strategy(String value) {
    String normalized = value == null || value.isBlank()
        ? "SERIAL_WAIT"
        : value.trim().toUpperCase(Locale.ROOT);
    if (!STRATEGIES.contains(normalized)) {
      throw new IllegalArgumentException("补数/补跑仅支持 SERIAL_WAIT 或 PARALLEL：" + normalized);
    }
    return normalized;
  }

  private String name(WorkflowBackfillCreateDTO request, WorkflowSchedulePO schedule) {
    if (request.name() != null && !request.name().isBlank()) {
      String value = request.name().trim();
      if (value.length() > 120) throw new IllegalArgumentException("Backfill 名称不能超过 120 个字符");
      return value;
    }
    return schedule.getName() + " · " + request.startBusinessDate() + " ~ " + request.endBusinessDate();
  }
}
