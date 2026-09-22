package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Mutable authoring draft for one DATA_SERVICE development node. */
public record DevelopmentDataServiceDraft(
    @JsonSerialize(using = ToStringSerializer.class) long nodeId,
    DevelopmentDataServiceDefinition definition,
    long draftRevision,
    Instant createTime,
    Instant updateTime) {}
