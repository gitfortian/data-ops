package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Hierarchical directory used to organize data-development tasks. */
public record DevelopmentDirectory(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    @JsonSerialize(using = ToStringSerializer.class) Long parentId,
    String name,
    String path,
    Instant createTime,
    Instant updateTime) {
}
