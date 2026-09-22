package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;

/** Mutable authoring draft for one data-development node. */
public record DevelopmentTaskDraft(
    @JsonSerialize(using = ToStringSerializer.class) Long nodeId,
    TaskDefinition definition,
    long draftRevision,
    Instant createTime,
    Instant updateTime) {

  /** Publish commands must pin the exact mutable revision they were prepared against. */
  public boolean matchesRevision(long expectedRevision) {
    return draftRevision == expectedRevision;
  }
}
