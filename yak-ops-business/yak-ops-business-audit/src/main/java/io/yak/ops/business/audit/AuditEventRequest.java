package io.yak.ops.business.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured event command with optional idempotency and event-level classification overrides. */
public record AuditEventRequest(
    AuditEventType type,
    AuditEventCategory category,
    AuditEventStatus status,
    String eventKey,
    String resourceType,
    String resourceId,
    String message,
    String reasonCode,
    Map<String, ?> payload) {

  public AuditEventRequest {
    if (type == null) throw new IllegalArgumentException("type must not be null");
    eventKey = normalize(eventKey);
    resourceType = normalize(resourceType);
    resourceId = normalize(resourceId);
    message = normalize(message);
    reasonCode = normalize(reasonCode);
    payload =
        payload == null || payload.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }

  /** Backward-compatible business event constructor used by existing callers. */
  public AuditEventRequest(
      AuditEventType type,
      String eventKey,
      String message,
      String reasonCode,
      Map<String, ?> payload) {
    this(type, null, null, eventKey, null, null, message, reasonCode, payload);
  }

  public static AuditEventRequest of(
      AuditEventType type, String eventKey, String message, Map<String, ?> payload) {
    return new AuditEventRequest(type, eventKey, message, null, payload);
  }

  public static AuditEventRequest authorization(
      AuditAuthorizationDecision decision, String eventKey) {
    if (decision == null) throw new IllegalArgumentException("decision must not be null");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.putAll(decision.attributes());
    payload.put("permission", decision.permission());
    payload.put("decision", decision.decision().name());
    return new AuditEventRequest(
        AuditEventType.AUTHORIZATION_DECISION,
        AuditEventCategory.AUTHORIZATION,
        decision.allowed() ? AuditEventStatus.SUCCESS : AuditEventStatus.FAILURE,
        eventKey,
        decision.resourceType(),
        decision.resourceId(),
        decision.allowed() ? "Authorization allowed" : "Authorization denied",
        decision.reasonCode(),
        payload);
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
