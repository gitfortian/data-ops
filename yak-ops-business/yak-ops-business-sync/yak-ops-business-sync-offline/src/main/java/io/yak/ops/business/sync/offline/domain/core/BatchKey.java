package io.yak.ops.business.sync.offline.domain.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** Stable business identity for one offline batch inside a task. */
public record BatchKey(String value) {

  public BatchKey {
    value = requireText(value, "BatchKey 不能为空");
  }

  public static BatchKey manual(String requestId) {
    return new BatchKey("manual:" + encode(requireText(requestId, "requestId 不能为空")));
  }

  public static BatchKey schedule(String scheduleId, Instant plannedFireTime) {
    Objects.requireNonNull(plannedFireTime, "plannedFireTime 不能为空");
    return new BatchKey(
        "schedule:"
            + encode(requireText(scheduleId, "scheduleId 不能为空"))
            + ":"
            + encode(plannedFireTime.toString()));
  }

  public static BatchKey workflow(String executionIdentity) {
    return new BatchKey(
        "workflow:" + encode(requireText(executionIdentity, "workflow identity 不能为空")));
  }

  public static BatchKey backfill(String requestId, String scopeFingerprint) {
    return new BatchKey(
        "backfill:"
            + encode(requireText(requestId, "backfillRequestId 不能为空"))
            + ":"
            + encode(requireText(scopeFingerprint, "scopeFingerprint 不能为空")));
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
