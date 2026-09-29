package io.yak.ops.business.audit;

/** Immutable correlation snapshot that can cross threads, workers and traces. */
public record AuditCarrier(
    String operationId,
    String actorId,
    String actorName,
    String actorType,
    Long projectId,
    String projectName,
    String resourceType,
    String resourceId,
    String resourceName,
    String source) {

  public AuditCarrier {
    operationId = requireText(operationId, "operationId must not be blank");
    actorId = normalize(actorId);
    actorName = normalize(actorName);
    actorType = normalize(actorType);
    projectName = normalize(projectName);
    resourceType = normalize(resourceType);
    resourceId = normalize(resourceId);
    resourceName = normalize(resourceName);
    source = normalize(source);
    if (source == null) source = "APPLICATION";
  }

  public AuditCarrier withResource(String id, String name) {
    return new AuditCarrier(
        operationId,
        actorId,
        actorName,
        actorType,
        projectId,
        projectName,
        resourceType,
        id,
        name,
        source);
  }

  private static String requireText(String value, String message) {
    String normalized = normalize(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
