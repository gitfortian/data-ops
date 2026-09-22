package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.DatasetVersionDraft;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Domain repository for the Dataset aggregate. */
public interface DatasetRepository {

  long insertDataset(String name, String description);

  long insertDevelopmentNodeDataset(long developmentNodeId, String name, String description);

  /** Appends one immutable version and its field contract atomically inside the caller transaction. */
  long appendVersion(DatasetVersionDraft draft);

  void updateCurrentVersion(long datasetId, long versionId);

  void updateStatus(long datasetId, DatasetStatus status);

  void updateMetadata(long datasetId, String name, String description);

  Optional<Dataset> findDataset(long datasetId);

  Optional<Dataset> findDatasetBySourceTaskAssetId(long sourceTaskAssetId);

  Optional<Dataset> findDatasetByDevelopmentNodeId(long developmentNodeId);

  List<Dataset> listDatasets();

  List<Dataset> listDatasetsByIds(Collection<Long> datasetIds);

  Optional<DatasetVersion> findVersion(long versionId);

  Optional<DatasetVersion> findVersion(long datasetId, int versionNo);

  List<DatasetVersion> listVersions(long datasetId);

  List<DatasetVersion> listVersionsByIds(Collection<Long> versionIds);

  List<DatasetField> listFields(long versionId);

  List<DatasetField> listFieldsByVersionIds(Collection<Long> versionIds);

  int nextVersionNo(long datasetId);

  void updateDraft(long datasetId, String draftDataSourceId, String draftSql);

  /** Returns the draft dataSourceId and sql saved by the editor, or nulls if never saved. */
  DraftSource loadDraftSource(long datasetId);

  void saveDraftFields(long datasetId, List<DatasetFieldSpec> fields);

  List<DatasetFieldSpec> loadDraftFields(long datasetId);

  record DraftSource(String dataSourceId, String sql) {}
}
