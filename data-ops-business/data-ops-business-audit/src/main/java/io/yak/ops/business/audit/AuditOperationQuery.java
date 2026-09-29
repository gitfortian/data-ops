package io.yak.ops.business.audit;

import java.time.LocalDateTime;

/** Read-side filters for the administrator Audit Center. */
public record AuditOperationQuery(
    int page,
    int size,
    String keyword,
    String actor,
    Long projectId,
    String operationType,
    String resourceType,
    String status,
    String source,
    LocalDateTime startTime,
    LocalDateTime endTime) {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  public AuditOperationQuery {
    page = page <= 0 ? 1 : page;
    size = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    keyword = normalize(keyword);
    actor = normalize(actor);
    projectId = projectId != null && projectId <= 0L ? null : projectId;
    operationType = normalize(operationType);
    resourceType = normalize(resourceType);
    status = normalize(status);
    source = normalize(source);
    if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
      LocalDateTime swap = startTime;
      startTime = endTime;
      endTime = swap;
    }
  }

  public static AuditOperationQuery empty() {
    return new AuditOperationQuery(
        1, DEFAULT_PAGE_SIZE, null, null, null, null, null, null, null, null, null);
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
