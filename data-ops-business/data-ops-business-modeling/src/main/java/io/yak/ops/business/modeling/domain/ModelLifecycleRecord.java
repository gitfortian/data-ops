package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;

public record ModelLifecycleRecord(
    Long id,
    String objectType,
    Long objectId,
    String fromStatus,
    String toStatus,
    String operator,
    String reason,
    LocalDateTime createdTime) {

  public static ModelLifecycleRecord create(
      String objectType,
      Long objectId,
      String fromStatus,
      String toStatus,
      String operator,
      String reason) {
    if (objectType == null || objectId == null) {
      throw new IllegalArgumentException("Lifecycle target must not be null");
    }
    return new ModelLifecycleRecord(null, objectType, objectId, fromStatus, toStatus, operator, reason, null);
  }
}
