package io.yak.ops.business.development.execution.model;

import java.time.LocalDateTime;
import java.util.Map;

/** Full execution-history projection used by the run-history detail API. */
public record DevelopmentTaskExecutionDetail(
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
    String content,
    String configJson,
    Map<String, Object> output,
    LocalDateTime startTime,
    LocalDateTime endTime) {}
