package io.yak.ops.spi.task.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Stable reference to one immutable published task revision. */
public record TaskRevisionRef(
    @JsonSerialize(using = ToStringSerializer.class) Long taskAssetId,
    @JsonSerialize(using = ToStringSerializer.class) Long taskRevisionId,
    int revisionNo) {
}
