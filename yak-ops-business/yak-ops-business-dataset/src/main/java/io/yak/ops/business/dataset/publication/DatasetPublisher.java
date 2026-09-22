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
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes exact upstream snapshots into immutable DatasetVersion aggregates. */
@Component
public class DatasetPublisher {

  private final DatasetRepository repository;
  private final DatasetReader reader;
  private final DatasetTaskCatalogGateway taskCatalogGateway;
  private final DatasetSchemaDiscovery schemaDiscovery;
  private final DatasetFieldNormalizer fieldNormalizer;
  private final DatasetVersionWriter versionWriter;
  private final DatasetLineageRefreshPublisher lineagePublisher;

  public DatasetPublisher(
      DatasetRepository repository,
      DatasetReader reader,
      DatasetTaskCatalogGateway taskCatalogGateway,
      DatasetSchemaDiscovery schemaDiscovery,
      DatasetFieldNormalizer fieldNormalizer,
      DatasetVersionWriter versionWriter,
      DatasetLineageRefreshPublisher lineagePublisher) {
    this.repository = repository;
    this.reader = reader;
    this.taskCatalogGateway = taskCatalogGateway;
    this.schemaDiscovery = schemaDiscovery;
    this.fieldNormalizer = fieldNormalizer;
    this.versionWriter = versionWriter;
    this.lineagePublisher = lineagePublisher;
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publish(DatasetPublishCommand command) {
    if (command == null) {
      throw new NullPointerException("command");
    }
    DatasetTaskAssetSnapshot asset = requirePublishableAsset(command.sourceTaskAssetId());
    return createDataset(asset, command);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publishFromRelease(DatasetPublishCommand command) {
    if (command == null) {
      throw new NullPointerException("command");
    }
    DatasetTaskAssetSnapshot asset = requirePublishableAsset(command.sourceTaskAssetId());
    Optional<Dataset> existing = repository.findDatasetBySourceTaskAssetId(asset.id());
    if (existing.isEmpty()) {
      return createDataset(asset, command);
    }

    long datasetId = existing.get().id();
    DatasetDetail current = reader.require(datasetId);
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null) {
      List<DatasetFieldSpec> fields = resolveFields(datasetId, asset, command.fields());
      versionWriter.appendNextQueryRevision(
          datasetId,
          asset.id(),
          asset.currentRevisionId(),
          asset.currentRevisionNo(),
          fields);
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

    List<DatasetFieldSpec> fields = resolveFields(datasetId, asset, command.fields());
    versionWriter.appendNextQueryRevision(
        datasetId,
        asset.id(),
        asset.currentRevisionId(),
        asset.currentRevisionNo(),
        fields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail createVersion(long datasetId, List<DatasetFieldSpec> fields) {
    DatasetDetail current = reader.require(datasetId);
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null) {
      throw new IllegalStateException("Dataset 尚未建立当前版本：" + datasetId);
    }
    if (currentVersion.sourceTaskAssetId() <= 0L) {
      throw new IllegalStateException("当前 DatasetVersion 不是 QUERY_REVISION 来源：" + datasetId);
    }

    DatasetTaskAssetSnapshot asset = requirePublishableAsset(currentVersion.sourceTaskAssetId());
    if (asset.currentRevisionId() == currentVersion.sourceTaskRevisionId()) {
      throw new IllegalArgumentException(
          "当前 TaskRevision 已经是 Dataset 的当前版本：V" + currentVersion.versionNo());
    }

    List<DatasetFieldSpec> normalizedFields = resolveFields(datasetId, asset, fields);
    versionWriter.appendNextQueryRevision(
        datasetId,
        asset.id(),
        asset.currentRevisionId(),
        asset.currentRevisionNo(),
        normalizedFields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  public List<DatasetFieldSpec> previewReleaseFields(long sourceTaskAssetId) {
    return schemaDiscovery.preview(requirePublishableAsset(sourceTaskAssetId));
  }

  public DatasetTaskAssetSnapshot requirePublishableAsset(long assetId) {
    DatasetTaskAssetSnapshot asset = taskCatalogGateway.get(assetId);
    if (asset.sourceOrigin() != SourceOrigin.DATA_DEVELOPMENT) {
      throw new IllegalArgumentException("只有数据开发 TaskAsset 可以发布为 Dataset：" + assetId);
    }
    if (asset.availability() != SourceAvailability.ONLINE) {
      throw new IllegalArgumentException("只有 ONLINE 的 TaskAsset 可以发布/更新 Dataset：" + assetId);
    }
    if (!"SQL".equalsIgnoreCase(asset.taskType())) {
      throw new IllegalArgumentException("当前仅支持 SQL TaskAsset 发布为 QUERY_REVISION Dataset");
    }
    if (asset.currentRevisionId() <= 0L || asset.currentRevisionNo() <= 0) {
      throw new IllegalStateException("TaskAsset 缺少有效的当前不可变版本：" + assetId);
    }
    return asset;
  }

  private DatasetDetail createDataset(
      DatasetTaskAssetSnapshot asset, DatasetPublishCommand command) {
    long datasetId =
        repository.insertDataset(
            normalizeName(command.name(), asset.name()), normalizeDescription(command.description()));
    List<DatasetFieldSpec> fields = resolveFields(datasetId, asset, command.fields());
    versionWriter.appendInitialQueryRevision(
        datasetId,
        asset.id(),
        asset.currentRevisionId(),
        asset.currentRevisionNo(),
        fields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  private List<DatasetFieldSpec> resolveFields(
      long datasetId, DatasetTaskAssetSnapshot asset, List<DatasetFieldSpec> requestedFields) {
    if (requestedFields != null && !requestedFields.isEmpty()) {
      return fieldNormalizer.normalize(datasetId, requestedFields);
    }
    return fieldNormalizer.normalize(datasetId, schemaDiscovery.discover(datasetId, asset));
  }

  private String normalizeName(String value, String fallback) {
    String normalized = value == null || value.isBlank() ? fallback : value.trim();
    if (normalized == null || normalized.isBlank()) {
      throw new IllegalArgumentException("Dataset 名称不能为空");
    }
    if (normalized.length() > 200) {
      throw new IllegalArgumentException("Dataset 名称不能超过 200 个字符");
    }
    return normalized;
  }

  private String normalizeDescription(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > 2000) {
      throw new IllegalArgumentException("Dataset 描述不能超过 2000 个字符");
    }
    return normalized;
  }
}
