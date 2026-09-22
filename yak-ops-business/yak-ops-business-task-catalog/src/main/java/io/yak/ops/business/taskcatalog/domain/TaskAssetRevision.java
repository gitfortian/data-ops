package io.yak.ops.business.taskcatalog.domain;

import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import java.util.Objects;

/** A catalog asset resolved to one immutable source-owned revision. */
public record TaskAssetRevision(
    TaskAsset asset,
    TaskSourceRevision revision) {

  public TaskAssetRevision {
    asset = Objects.requireNonNull(asset, "asset");
    revision = Objects.requireNonNull(revision, "revision");
  }
}
