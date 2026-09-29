package io.yak.ops.business.audit;

import java.time.LocalDateTime;

/** One row in the Audit Center operation list. */
public record AuditOperationSummary(
    String operationId,
    String operationType,
    String operationName,
    String actorId,
    String actorName,
    Long projectId,
    String projectName,
    String resourceType,
    String resourceId,
    String resourceName,
    String status,
    String source,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long durationMillis,
    String rootTraceId,
    String errorCode,
    String summary) {}
