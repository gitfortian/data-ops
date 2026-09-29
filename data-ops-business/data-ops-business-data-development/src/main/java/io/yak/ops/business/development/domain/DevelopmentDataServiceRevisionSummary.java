package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Lightweight immutable Data Service Node revision metadata for editor history. */
public record DevelopmentDataServiceRevisionSummary(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    @JsonSerialize(using = ToStringSerializer.class) long nodeId,
    int revisionNo,
    long sourceDraftRevision,
    @JsonSerialize(using = ToStringSerializer.class) long sourceTaskRevisionId,
    int sourceTaskRevisionNo,
    String checksum,
    Instant createTime) {}
