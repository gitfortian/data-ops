package io.yak.ops.business.development.execution.model;

import java.time.LocalDateTime;

/** Lightweight row used by the data-development execution-history read side. */
public record DevelopmentTaskExecutionSummary(
    Long id,
    Long nodeId,
    String taskName,
    String taskType,
    int schemaVersion,
    String triggerType,
    String runtimeExecutionId,
    Long retryOfExecutionId,
    String status,
    String operatorName,
    Long durationMs,
    String errorMessage,
    LocalDateTime startTime,
    LocalDateTime endTime) {}
