package io.yak.ops.business.development.release.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.time.Instant;

/** Published data-development task exposed by the release-center read side. */
public record DevelopmentReleaseSummary(
    @JsonSerialize(using = ToStringSerializer.class) Long assetId,
    @JsonSerialize(using = ToStringSerializer.class) Long nodeId,
    String taskName,
    String taskType,
    TaskAssetStatus status,
    @JsonSerialize(using = ToStringSerializer.class) Long currentRevisionId,
    int currentRevisionNo,
    int latestRevisionNo,
    boolean hasNewerRevision,
    String checksum,
    Instant revisionCreateTime,
    Instant updateTime) {}
