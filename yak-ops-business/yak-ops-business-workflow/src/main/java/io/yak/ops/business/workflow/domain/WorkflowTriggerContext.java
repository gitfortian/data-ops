package io.yak.ops.business.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 工作流启动上下文。
 *
 * <p>Definition、调度、补数、运维补跑和 API 最终都通过同一个 Launch 入口进入 Runtime，
 * 该上下文只描述“为什么启动”，不参与 DAG 本身的编排语义。</p>
 */
public record WorkflowTriggerContext(
    WorkflowTriggerType triggerType,
    String triggerId,
    String scheduleId,
    String backfillId,
    Instant plannedFireTime,
    String timezone) {

  public WorkflowTriggerContext {
    if (triggerType == null) {
      throw new IllegalArgumentException("工作流 triggerType 不能为空");
    }
    triggerId = required(triggerId, "工作流 triggerId 不能为空");
    scheduleId = trimToNull(scheduleId);
    backfillId = trimToNull(backfillId);
    timezone = trimToNull(timezone);
  }

  /** Compatibility constructor without Backfill/timezone lineage. */
  public WorkflowTriggerContext(
      WorkflowTriggerType triggerType,
      String triggerId,
      String scheduleId,
      Instant plannedFireTime) {
    this(triggerType, triggerId, scheduleId, null, plannedFireTime, null);
  }

  public static WorkflowTriggerContext manual() {
    return new WorkflowTriggerContext(
        WorkflowTriggerType.MANUAL,
        generatedId("manual"),
        null,
        null,
        null,
        null);
  }

  public static WorkflowTriggerContext api() {
    return new WorkflowTriggerContext(
        WorkflowTriggerType.API,
        generatedId("api"),
        null,
        null,
        null,
        null);
  }

  public static WorkflowTriggerContext scheduled(
      String triggerId,
      String scheduleId,
      Instant plannedFireTime) {
    return scheduled(triggerId, scheduleId, plannedFireTime, null);
  }

  public static WorkflowTriggerContext scheduled(
      String triggerId,
      String scheduleId,
      Instant plannedFireTime,
      String timezone) {
    String safeScheduleId = required(scheduleId, "工作流 scheduleId 不能为空");
    if (plannedFireTime == null) {
      throw new IllegalArgumentException("调度 plannedFireTime 不能为空");
    }
    return new WorkflowTriggerContext(
        WorkflowTriggerType.SCHEDULE,
        triggerId,
        safeScheduleId,
        null,
        plannedFireTime,
        timezone);
  }

  public static WorkflowTriggerContext backfill(
      String triggerId,
      String scheduleId,
      Instant plannedFireTime) {
    return new WorkflowTriggerContext(
        WorkflowTriggerType.BACKFILL,
        triggerId,
        scheduleId,
        null,
        required(plannedFireTime, "补数 plannedFireTime 不能为空"),
        null);
  }

  public static WorkflowTriggerContext backfill(
      String triggerId,
      String scheduleId,
      String backfillId,
      Instant plannedFireTime,
      String timezone) {
    return new WorkflowTriggerContext(
        WorkflowTriggerType.BACKFILL,
        triggerId,
        required(scheduleId, "补数 scheduleId 不能为空"),
        required(backfillId, "补数 backfillId 不能为空"),
        required(plannedFireTime, "补数 plannedFireTime 不能为空"),
        timezone);
  }

  /** 指定 businessDate 的人工运维补跑。 */
  public static WorkflowTriggerContext rerun(
      String triggerId,
      String scheduleId,
      String backfillId,
      Instant plannedFireTime,
      String timezone) {
    return new WorkflowTriggerContext(
        WorkflowTriggerType.RERUN,
        triggerId,
        required(scheduleId, "补跑 scheduleId 不能为空"),
        required(backfillId, "补跑 batchId 不能为空"),
        required(plannedFireTime, "补跑 plannedFireTime 不能为空"),
        timezone);
  }

  private static String generatedId(String prefix) {
    return "workflow-trigger-" + prefix + "-" + UUID.randomUUID();
  }

  private static String required(String value, String message) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  private static Instant required(Instant value, String message) {
    if (value == null) throw new IllegalArgumentException(message);
    return value;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
