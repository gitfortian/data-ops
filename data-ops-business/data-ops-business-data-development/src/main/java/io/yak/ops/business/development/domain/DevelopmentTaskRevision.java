package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.Objects;

/** Immutable published task revision. */
public record DevelopmentTaskRevision(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    @JsonSerialize(using = ToStringSerializer.class) Long nodeId,
    int revisionNo,
    long sourceDraftRevision,
    TaskDefinition definition,
    String checksum,
    Instant createTime) {

  /** True when this immutable revision already represents the exact normalized draft snapshot. */
  public boolean represents(long draftRevision, String definitionChecksum) {
    return sourceDraftRevision == draftRevision && Objects.equals(checksum, definitionChecksum);
  }
}
