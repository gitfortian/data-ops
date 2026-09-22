package io.yak.ops.business.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Technology-agnostic authorization decision that can be attached to a business operation. */
public record AuditAuthorizationDecision(
    String permission,
    Decision decision,
    String reasonCode,
    String resourceType,
    String resourceId,
    String resourceName,
    Map<String, ?> attributes) {

  public enum Decision {
    ALLOW,
    DENY
  }

  public AuditAuthorizationDecision {
    permission = requireText(permission, "permission");
    if (decision == null) throw new IllegalArgumentException("decision must not be null");
    reasonCode = requireText(reasonCode, "reasonCode");
    resourceType = normalize(resourceType);
    resourceId = normalize(resourceId);
    resourceName = normalize(resourceName);
    attributes =
        attributes == null || attributes.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
  }

  public static AuditAuthorizationDecision allow(
      String permission,
      String reasonCode,
      String resourceType,
      String resourceId,
      String resourceName,
      Map<String, ?> attributes) {
    return new AuditAuthorizationDecision(
        permission, Decision.ALLOW, reasonCode, resourceType, resourceId, resourceName, attributes);
  }

  public static AuditAuthorizationDecision deny(
      String permission,
      String reasonCode,
      String resourceType,
      String resourceId,
      String resourceName,
      Map<String, ?> attributes) {
    return new AuditAuthorizationDecision(
        permission, Decision.DENY, reasonCode, resourceType, resourceId, resourceName, attributes);
  }

  public boolean allowed() {
    return decision == Decision.ALLOW;
  }

  private static String requireText(String value, String field) {
    String normalized = normalize(value);
    if (normalized == null) throw new IllegalArgumentException(field + " must not be blank");
    return normalized;
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
