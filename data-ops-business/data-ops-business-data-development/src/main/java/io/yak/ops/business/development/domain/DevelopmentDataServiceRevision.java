package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Immutable published Data Service Node revision. This is not a Task Catalog revision. */
public record DevelopmentDataServiceRevision(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    @JsonSerialize(using = ToStringSerializer.class) long nodeId,
    int revisionNo,
    long sourceDraftRevision,
    DevelopmentDataServiceDefinition definition,
    String checksum,
    Instant createTime) {}
