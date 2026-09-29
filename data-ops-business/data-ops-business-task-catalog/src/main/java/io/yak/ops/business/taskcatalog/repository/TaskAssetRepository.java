package io.yak.ops.business.taskcatalog.repository;

import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.util.List;
import java.util.Optional;

/** Persistent Task Catalog port. */
public interface TaskAssetRepository {

  TaskAsset upsertPublished(
      TaskAssetSource source,
      String sourceRef,
      Long projectId,
      String name,
      String taskType,
      long revisionId,
      int revisionNo);

  Optional<TaskAsset> findById(long assetId);

  Optional<TaskAsset> findBySource(TaskAssetSource source, String sourceRef);

  List<TaskAsset> list(TaskAssetSource source, TaskAssetStatus status, String keyword);

  boolean updateSourceMetadata(
      TaskAssetSource source,
      String sourceRef,
      Long projectId,
      String name,
      String taskType);

  boolean updateStatus(TaskAssetSource source, String sourceRef, TaskAssetStatus status);
}
