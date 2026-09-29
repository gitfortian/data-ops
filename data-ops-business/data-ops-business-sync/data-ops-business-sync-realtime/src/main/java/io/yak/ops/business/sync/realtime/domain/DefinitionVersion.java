package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable published realtime synchronization definition. */
public record DefinitionVersion(
    long id,
    long taskId,
    int versionNo,
    int sourceDraftRevision,
    SyncDefinition definition,
    RuntimeEnvironmentRef runtimeEnvironment,
    DefinitionDigest definitionDigest,
    LocalDateTime publishedAt) {

  public DefinitionVersion {
    if (id <= 0) throw new IllegalArgumentException("DefinitionVersionId 必须大于 0");
    if (taskId <= 0) throw new IllegalArgumentException("TaskId 必须大于 0");
    if (versionNo <= 0) throw new IllegalArgumentException("VersionNo 必须大于 0");
    if (sourceDraftRevision <= 0) {
      throw new IllegalArgumentException("Source DraftRevision 必须大于 0");
    }
    definition = Objects.requireNonNull(definition, "SyncDefinition 不能为空");
    runtimeEnvironment =
        Objects.requireNonNull(runtimeEnvironment, "RuntimeEnvironmentRef 不能为空");
    definitionDigest = Objects.requireNonNull(definitionDigest, "DefinitionDigest 不能为空");
    publishedAt = Objects.requireNonNull(publishedAt, "publishedAt 不能为空");
  }
}
