package io.yak.ops.business.development.execution.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import java.util.Map;

/** Full execution-history projection used by the run-history detail API. */
public record DevelopmentTaskExecutionDetail(
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
    String content,
    String configJson,
    Map<String, Object> output,
    LocalDateTime startTime,
    LocalDateTime endTime) {}
