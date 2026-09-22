package io.yak.ops.business.workflow.domain;

import java.time.Instant;

/** Trigger Ledger 数据库幂等键。正常计划/Misfire 共用键，Backfill 按批次隔离。 */
public final class WorkflowScheduleTriggerIdentity {
  private WorkflowScheduleTriggerIdentity() {
  }

  public static String scheduled(String scheduleId, Instant plannedFireTime) {
    return required(scheduleId, "scheduleId 不能为空")
        + "|SCHEDULE|"
        + required(plannedFireTime).toEpochMilli();
  }

  public static String backfill(
      String scheduleId,
      String backfillId,
      Instant plannedFireTime) {
    return required(scheduleId, "scheduleId 不能为空")
        + "|BACKFILL|"
        + required(backfillId, "backfillId 不能为空")
        + "|"
        + required(plannedFireTime).toEpochMilli();
  }

  private static String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private static Instant required(Instant value) {
    if (value == null) throw new IllegalArgumentException("plannedFireTime 不能为空");
    return value;
  }
}
