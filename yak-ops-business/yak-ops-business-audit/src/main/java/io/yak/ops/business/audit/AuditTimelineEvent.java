package io.yak.ops.business.audit;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One ordered event in an AuditOperation Timeline. */
public record AuditTimelineEvent(
    Long id,
    String eventType,
    String eventCategory,
    String eventStatus,
    LocalDateTime occurredAt,
    String actorId,
    String resourceType,
    String resourceId,
    String traceId,
    String spanId,
    Long parentEventId,
    String reasonCode,
    String message,
    String title,
    String description,
    Map<String, Object> payload) {

  public AuditTimelineEvent {
    payload =
        payload == null || payload.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }
}
