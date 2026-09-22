package io.yak.ops.business.modeling.repository;

import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.domain.ModelVersionSummary;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for immutable model version snapshots. */
public interface ModelVersionRepository {

  /** Inserts a new version row and returns the persisted domain object. */
  ModelVersion insert(
      Long modelId, int versionNo, String structureJson, String metaJson,
      int columnCount, String checksum, String publishedBy);

  /** Lists all versions for a model, newest first (lightweight, no structure_json). */
  List<ModelVersionSummary> listByModelId(Long modelId);

  /** Finds one version by model + version number. */
  Optional<ModelVersion> findByVersionNo(Long modelId, int versionNo);

  /** Finds one version by primary key; empty when it belongs to another model. */
  Optional<ModelVersion> findById(Long versionId, Long modelId);

  /** Finds the latest version for a model (or empty if never published). */
  Optional<ModelVersion> findLatestByModelId(Long modelId);

  /** Returns the next version number (1-based; SQL MAX+1). */
  int nextVersionNo(Long modelId);
}
