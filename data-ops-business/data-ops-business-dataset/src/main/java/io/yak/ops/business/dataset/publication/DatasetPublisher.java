package io.yak.ops.business.dataset.publication;

import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceAvailability;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceOrigin;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.util.List;
import org.springframework.stereotype.Component;

/** Prepares exact source/schema evidence before delegating bounded writes to a transaction role. */
@Component
public class DatasetPublisher {

  private final DatasetRepository repository;
  private final DatasetReader reader;
  private final DatasetTaskCatalogGateway taskCatalogGateway;
  private final DatasetSchemaDiscovery schemaDiscovery;
  private final DatasetPublicationTransaction publicationTransaction;

  public DatasetPublisher(
      DatasetRepository repository,
      DatasetReader reader,
      DatasetTaskCatalogGateway taskCatalogGateway,
      DatasetSchemaDiscovery schemaDiscovery,
      DatasetPublicationTransaction publicationTransaction) {
    this.repository = repository;
    this.reader = reader;
    this.taskCatalogGateway = taskCatalogGateway;
    this.schemaDiscovery = schemaDiscovery;
    this.publicationTransaction = publicationTransaction;
  }

  public DatasetDetail publish(DatasetPublishCommand command) {
    if (command == null) {
      throw new NullPointerException("command");
    }
    DatasetTaskAssetSnapshot asset = requirePublishableAsset(command.sourceTaskAssetId());
    List<DatasetFieldSpec> fields = prepareFields(asset, command.fields());
    return publicationTransaction.publish(
        asset,
        normalizeName(command.name(), asset.name()),
        normalizeDescription(command.description()),
        fields);
  }

  public DatasetDetail publishFromRelease(DatasetPublishCommand command) {
    if (command == null) {
      throw new NullPointerException("command");
    }
    DatasetTaskAssetSnapshot asset = requirePublishableAsset(command.sourceTaskAssetId());
    DatasetDetail current = findReleaseDataset(asset.id());
    if (isCurrentRevision(current, asset)) {
      return current;
    }

    List<DatasetFieldSpec> fields = prepareFields(asset, command.fields());
    return publicationTransaction.publishFromRelease(
        asset,
        normalizeName(command.name(), asset.name()),
        normalizeDescription(command.description()),
        fields);
  }

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

    List<DatasetFieldSpec> preparedFields = prepareFields(asset, fields);
    return publicationTransaction.createVersion(
        datasetId,
        asset,
        currentVersion.sourceTaskRevisionId(),
        preparedFields);
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

  private DatasetDetail findReleaseDataset(long sourceTaskAssetId) {
    return repository.findDatasetBySourceTaskAssetId(sourceTaskAssetId)
        .map(dataset -> reader.require(dataset.id()))
        .orElse(null);
  }

  private boolean isCurrentRevision(
      DatasetDetail current, DatasetTaskAssetSnapshot asset) {
    return current != null
        && current.currentVersion() != null
        && current.currentVersion().sourceTaskAssetId() == asset.id()
        && current.currentVersion().sourceTaskRevisionId() == asset.currentRevisionId();
  }

  private List<DatasetFieldSpec> prepareFields(
      DatasetTaskAssetSnapshot asset, List<DatasetFieldSpec> requestedFields) {
    return requestedFields != null && !requestedFields.isEmpty()
        ? List.copyOf(requestedFields)
        : schemaDiscovery.preview(asset);
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
