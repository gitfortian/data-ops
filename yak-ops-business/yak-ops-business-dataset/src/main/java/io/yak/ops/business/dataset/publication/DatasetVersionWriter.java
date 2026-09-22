package io.yak.ops.business.dataset.publication;

import io.yak.ops.business.dataset.DatasetVersionDraft;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import java.util.List;
import org.springframework.stereotype.Component;

/** Appends immutable DatasetVersion aggregates and moves only the Dataset current-version pointer. */
@Component
public class DatasetVersionWriter {

  private final DatasetRepository repository;
  private final DatasetFieldNormalizer fieldNormalizer;

  public DatasetVersionWriter(
      DatasetRepository repository, DatasetFieldNormalizer fieldNormalizer) {
    this.repository = repository;
    this.fieldNormalizer = fieldNormalizer;
  }

  public long appendInitialQueryRevision(
      long datasetId,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      List<DatasetFieldSpec> fields) {
    return appendQueryRevision(
        datasetId,
        sourceTaskAssetId,
        sourceTaskRevisionId,
        sourceTaskRevisionNo,
        fields,
        false);
  }

  public long appendNextQueryRevision(
      long datasetId,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      List<DatasetFieldSpec> fields) {
    return appendQueryRevision(
        datasetId,
        sourceTaskAssetId,
        sourceTaskRevisionId,
        sourceTaskRevisionNo,
        fields,
        true);
  }

  public long appendInitialSqlQuery(
      long datasetId, String dataSourceId, String sql, List<DatasetFieldSpec> fields) {
    return appendSqlQuery(datasetId, dataSourceId, sql, fields, false);
  }

  public long appendNextSqlQuery(
      long datasetId, String dataSourceId, String sql, List<DatasetFieldSpec> fields) {
    return appendSqlQuery(datasetId, dataSourceId, sql, fields, true);
  }

  private long appendQueryRevision(
      long datasetId,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      List<DatasetFieldSpec> fields,
      boolean requireExistingDataset) {
    requireDatasetIfNecessary(datasetId, requireExistingDataset);
    int versionNo = repository.nextVersionNo(datasetId);
    long versionId =
        repository.appendVersion(
            DatasetVersionDraft.queryRevision(
                datasetId,
                versionNo,
                sourceTaskAssetId,
                sourceTaskRevisionId,
                sourceTaskRevisionNo,
                fieldNormalizer.definitions(fields)));
    repository.updateCurrentVersion(datasetId, versionId);
    return versionId;
  }

  private long appendSqlQuery(
      long datasetId,
      String dataSourceId,
      String sql,
      List<DatasetFieldSpec> fields,
      boolean requireExistingDataset) {
    requireDatasetIfNecessary(datasetId, requireExistingDataset);
    int versionNo = repository.nextVersionNo(datasetId);
    long versionId =
        repository.appendVersion(
            DatasetVersionDraft.sqlQuery(
                datasetId,
                versionNo,
                dataSourceId,
                sql,
                fieldNormalizer.definitions(fields)));
    repository.updateCurrentVersion(datasetId, versionId);
    return versionId;
  }

  private void requireDatasetIfNecessary(long datasetId, boolean required) {
    if (!required) {
      return;
    }
    repository
        .findDataset(datasetId)
        .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));
  }
}
