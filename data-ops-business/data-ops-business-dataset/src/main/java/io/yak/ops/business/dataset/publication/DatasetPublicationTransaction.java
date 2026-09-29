package io.yak.ops.business.dataset.publication;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceAvailability;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceOrigin;
import io.yak.ops.business.dataset.lineage.DatasetLineageRefreshPublisher;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies prepared Dataset publication data in short business transactions. */
@Component
public class DatasetPublicationTransaction {

  private final DatasetRepository repository;
  private final DatasetReader reader;
  private final DatasetTaskCatalogGateway taskCatalogGateway;
  private final DatasetFieldNormalizer fieldNormalizer;
  private final DatasetVersionWriter versionWriter;
  private final DatasetLineageRefreshPublisher lineagePublisher;

  public DatasetPublicationTransaction(
      DatasetRepository repository,
      DatasetReader reader,
      DatasetTaskCatalogGateway taskCatalogGateway,
      DatasetFieldNormalizer fieldNormalizer,
      DatasetVersionWriter versionWriter,
      DatasetLineageRefreshPublisher lineagePublisher) {
    this.repository = repository;
    this.reader = reader;
    this.taskCatalogGateway = taskCatalogGateway;
    this.fieldNormalizer = fieldNormalizer;
    this.versionWriter = versionWriter;
    this.lineagePublisher = lineagePublisher;
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publish(
      DatasetTaskAssetSnapshot asset,
      String name,
      String description,
      List<DatasetFieldSpec> preparedFields) {
    requireCurrentAsset(asset);
    long datasetId = repository.insertDataset(name, description);
    appendInitialVersion(datasetId, asset, preparedFields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publishFromRelease(
      DatasetTaskAssetSnapshot asset,
      String name,
      String description,
      List<DatasetFieldSpec> preparedFields) {
    repository.lockSourceTaskAsset(asset.id());
    requireCurrentAsset(asset);

    Optional<Dataset> existing = repository.findDatasetBySourceTaskAssetId(asset.id());
    if (existing.isEmpty()) {
      long datasetId = repository.insertDataset(name, description);
      appendInitialVersion(datasetId, asset, preparedFields);
      lineagePublisher.request(datasetId);
      return reader.require(datasetId);
    }

    long datasetId = existing.get().id();
    DatasetDetail current = reader.require(datasetId);
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null) {
      appendNextVersion(datasetId, asset, preparedFields);
      lineagePublisher.request(datasetId);
      return reader.require(datasetId);
    }
    if (currentVersion.sourceTaskAssetId() != asset.id()) {
      throw new IllegalStateException(
          "Dataset 来源 TaskAsset 不一致：datasetId="
              + datasetId
              + ", expected="
              + currentVersion.sourceTaskAssetId()
              + ", actual="
              + asset.id());
    }
    if (currentVersion.sourceTaskRevisionId() == asset.currentRevisionId()) {
      return current;
    }

    appendNextVersion(datasetId, asset, preparedFields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail createVersion(
      long datasetId,
      DatasetTaskAssetSnapshot asset,
      long expectedCurrentSourceRevisionId,
      List<DatasetFieldSpec> preparedFields) {
    repository.lockDatasetForUpdate(datasetId);
    DatasetDetail current = reader.require(datasetId);
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null || currentVersion.sourceTaskAssetId() <= 0L) {
      throw new IllegalStateException(
          "当前 DatasetVersion 不是 QUERY_REVISION 来源：" + datasetId);
    }
    if (currentVersion.sourceTaskAssetId() != asset.id()) {
      throw new IllegalStateException("Dataset 来源 TaskAsset 已变化：" + datasetId);
    }
    if (currentVersion.sourceTaskRevisionId() != expectedCurrentSourceRevisionId) {
      throw new IllegalStateException("Dataset 当前版本已被其他请求更新，请刷新后重试：" + datasetId);
    }
    if (asset.currentRevisionId() == currentVersion.sourceTaskRevisionId()) {
      throw new IllegalArgumentException(
          "当前 TaskRevision 已经是 Dataset 的当前版本：V" + currentVersion.versionNo());
    }
    requireCurrentAsset(asset);

    appendNextVersion(datasetId, asset, preparedFields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  private void appendInitialVersion(
      long datasetId, DatasetTaskAssetSnapshot asset, List<DatasetFieldSpec> fields) {
    versionWriter.appendInitialQueryRevision(
        datasetId,
        asset.id(),
        asset.currentRevisionId(),
        asset.currentRevisionNo(),
        fieldNormalizer.normalize(datasetId, fields));
  }

  private void appendNextVersion(
      long datasetId, DatasetTaskAssetSnapshot asset, List<DatasetFieldSpec> fields) {
    versionWriter.appendNextQueryRevision(
        datasetId,
        asset.id(),
        asset.currentRevisionId(),
        asset.currentRevisionNo(),
        fieldNormalizer.normalize(datasetId, fields));
  }

  private void requireCurrentAsset(DatasetTaskAssetSnapshot expected) {
    DatasetTaskAssetSnapshot current = taskCatalogGateway.get(expected.id());
    if (current.sourceOrigin() != SourceOrigin.DATA_DEVELOPMENT
        || current.availability() != SourceAvailability.ONLINE
        || !"SQL".equalsIgnoreCase(current.taskType())) {
      throw new IllegalStateException("TaskAsset 发布条件已变化，请刷新后重试：" + expected.id());
    }
    if (current.currentRevisionId() != expected.currentRevisionId()
        || current.currentRevisionNo() != expected.currentRevisionNo()) {
      throw new IllegalStateException("TaskAsset 当前 Revision 已变化，请重新执行发布：" + expected.id());
    }
  }
}
