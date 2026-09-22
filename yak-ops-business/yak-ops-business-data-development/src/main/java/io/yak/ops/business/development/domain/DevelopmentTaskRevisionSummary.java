package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Lightweight immutable revision metadata used by the versions panel. */
public record DevelopmentTaskRevisionSummary(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    @JsonSerialize(using = ToStringSerializer.class) Long nodeId,
    int revisionNo,
    long sourceDraftRevision,
    String checksum,
    Instant createTime) {
}
