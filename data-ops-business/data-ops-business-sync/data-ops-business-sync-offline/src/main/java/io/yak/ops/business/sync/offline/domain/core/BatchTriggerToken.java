package io.yak.ops.business.sync.offline.domain.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/** Application-boundary token carrying stable Batch identity for trigger submission. */
public final class BatchTriggerToken {

  public static final String MANUAL = "MANUAL";
  public static final String SCHEDULE = "SCHEDULE";
  public static final String WORKFLOW = "WORKFLOW";
  public static final String BACKFILL = "BACKFILL";
  public static final String RETRY = "RETRY";

  private static final String SCHEDULE_PREFIX = SCHEDULE + "@";

  private BatchTriggerToken() {}

  public static String schedule(String scheduleId, Instant plannedFireTime) {
    Objects.requireNonNull(plannedFireTime, "plannedFireTime 不能为空");
    return SCHEDULE_PREFIX
        + encode(requireText(scheduleId, "scheduleId 不能为空"))
        + "@"
        + encode(plannedFireTime.toString());
  }

  public static Parsed parse(String triggerType) {
    String value = requireText(triggerType, "triggerType 不能为空");
    if (value.startsWith(SCHEDULE_PREFIX)) {
      String payload = value.substring(SCHEDULE_PREFIX.length());
      String[] parts = payload.split("@", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("SCHEDULE trigger 缺少稳定 Batch identity");
      }
      String scheduleId = decode(parts[0]);
      Instant plannedFireTime = Instant.parse(decode(parts[1]));
      return new Parsed(
          SCHEDULE,
          BatchTrigger.SCHEDULE,
          BatchKey.schedule(scheduleId, plannedFireTime));
    }

    String normalized = value.toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case SCHEDULE -> new Parsed(SCHEDULE, BatchTrigger.SCHEDULE, null);
      case WORKFLOW -> new Parsed(WORKFLOW, BatchTrigger.WORKFLOW, null);
      case BACKFILL -> new Parsed(BACKFILL, BatchTrigger.BACKFILL, null);
      case RETRY -> new Parsed(RETRY, null, null);
      default -> new Parsed(value, BatchTrigger.MANUAL, null);
    };
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(String value) {
    try {
      return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("trigger identity 编码不合法", exception);
    }
  }

  private static String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  public record Parsed(String attemptTriggerType, BatchTrigger batchTrigger, BatchKey batchKey) {

    public boolean retry() {
      return RETRY.equals(attemptTriggerType);
    }
  }
}
