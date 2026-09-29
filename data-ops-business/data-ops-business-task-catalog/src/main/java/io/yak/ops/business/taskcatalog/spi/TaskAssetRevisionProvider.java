package io.yak.ops.business.taskcatalog.spi;

import io.yak.ops.spi.task.model.TaskAssetSource;
import java.util.Optional;

/** Resolves immutable revisions owned by one TaskAsset source domain. */
public interface TaskAssetRevisionProvider {

  TaskAssetSource source();

  Optional<TaskSourceRevision> resolve(String sourceRef, long revisionId);
}
