package io.yak.ops.business.audit;

import java.util.Map;

/** Snapshot used to open one business audit operation. */
public record AuditOperationRequest(
    String operationType,
    String operationName,
    String resourceType,
    String resourceId,
    String resourceName,
    String source,
    Map<String, ?> metadata) {

  public AuditOperationRequest {
    if (operationType == null || operationType.isBlank()) {
      throw new IllegalArgumentException("operationType must not be blank");
    }
    if (operationName == null || operationName.isBlank()) {
      throw new IllegalArgumentException("operationName must not be blank");
    }
    source = source == null || source.isBlank() ? "APPLICATION" : source;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
