package io.yak.ops.business.taskcatalog.spi;

import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Objects;

/** Source-neutral immutable revision payload returned by a task authoring domain. */
public record TaskSourceRevision(
    long revisionId,
    int revisionNo,
    TaskDefinition definition,
    String checksum,
    Long sourceProjectId) {

  public TaskSourceRevision {
    if (revisionId <= 0L) throw new IllegalArgumentException("revisionId must be positive");
    if (revisionNo <= 0) throw new IllegalArgumentException("revisionNo must be positive");
    definition = Objects.requireNonNull(definition, "definition");
    if (sourceProjectId != null && sourceProjectId <= 0L) {
      throw new IllegalArgumentException("sourceProjectId must be positive when present");
    }
  }

  /** Compatibility constructor for producers that have not reached their Project Space stage yet. */
  public TaskSourceRevision(
      long revisionId,
      int revisionNo,
      TaskDefinition definition,
      String checksum) {
    this(revisionId, revisionNo, definition, checksum, null);
  }
}
