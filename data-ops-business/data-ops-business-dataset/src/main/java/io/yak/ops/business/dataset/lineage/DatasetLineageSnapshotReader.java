package io.yak.ops.business.dataset.lineage;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the current persisted Dataset snapshot required by derived-lineage projection. */
@Component
public class DatasetLineageSnapshotReader {

  private final DatasetRepository repository;

  public DatasetLineageSnapshotReader(DatasetRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public DatasetDetail require(long datasetId) {
    if (datasetId <= 0L) {
      throw new IllegalArgumentException("datasetId 必须大于 0");
    }
    Dataset dataset =
        repository
            .findDataset(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));

    DatasetVersion currentVersion = null;
    List<DatasetField> fields = List.of();
    if (dataset.currentVersionId() != null) {
      currentVersion =
          repository
              .findVersion(dataset.currentVersionId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Dataset 当前版本不存在：datasetId="
                              + datasetId
                              + ", versionId="
                              + dataset.currentVersionId()));
      fields = repository.listFields(currentVersion.id());
    }
    return new DatasetDetail(dataset, currentVersion, repository.listVersions(datasetId), fields);
  }
}
