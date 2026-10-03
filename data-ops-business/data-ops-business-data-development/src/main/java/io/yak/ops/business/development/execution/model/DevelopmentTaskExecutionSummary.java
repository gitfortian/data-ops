package io.yak.ops.business.development.execution.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

/** Lightweight row used by the data-development execution-history read side. */
public record DevelopmentTaskExecutionSummary(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    @JsonSerialize(using = ToStringSerializer.class) Long nodeId,
    String taskName,
    String taskType,
    int schemaVersion,
    String triggerType,
    String runtimeExecutionId,
    @JsonSerialize(using = ToStringSerializer.class) Long retryOfExecutionId,
    String status,
    String operatorName,
    Long durationMs,
    String failureReason,
    String errorMessage,
    LocalDateTime startTime,
    LocalDateTime endTime) {}
