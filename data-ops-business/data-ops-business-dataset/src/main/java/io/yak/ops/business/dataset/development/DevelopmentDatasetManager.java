package io.yak.ops.business.dataset.development;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetSqlSafety;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.lineage.DatasetLineageRefreshPublisher;
import io.yak.ops.business.dataset.publication.DatasetPublishCommand;
import io.yak.ops.business.dataset.publication.DatasetPublisher;
import io.yak.ops.business.dataset.publication.DatasetVersionWriter;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns the stable DevelopmentNode -> Dataset identity and its immutable versions. */
@Component
public class DevelopmentDatasetManager {

  /** Draft context exposed through the Facade for the editor to restore working state. */
  public record DraftContext(
      String dataSourceId,
      String sql,
      List<DatasetFieldSpec> fields) {
    public DraftContext {
      fields = fields == null ? List.of() : List.copyOf(fields);
    }
  }

  private final DatasetRepository repository;
  private final DatasetReader reader;
  private final DatasetPublisher publisher;
  private final DatasetSchemaDiscovery discovery;
  private final DatasetFieldNormalizer fieldNormalizer;
  private final DatasetVersionWriter versionWriter;
  private final DatasetLineageRefreshPublisher lineagePublisher;

  public DevelopmentDatasetManager(
      DatasetRepository repository,
      DatasetReader reader,
      DatasetPublisher publisher,
      DatasetSchemaDiscovery discovery,
      DatasetFieldNormalizer fieldNormalizer,
      DatasetVersionWriter versionWriter,
      DatasetLineageRefreshPublisher lineagePublisher) {
    this.repository = repository;
    this.reader = reader;
    this.publisher = publisher;
    this.discovery = discovery;
    this.fieldNormalizer = fieldNormalizer;
    this.versionWriter = versionWriter;
    this.lineagePublisher = lineagePublisher;
  }

  public Optional<DatasetDetail> find(long developmentNodeId) {
    return reader.findByDevelopmentNodeId(developmentNodeId);
  }

  /** Load the current draft context (dataSourceId, SQL, draft fields) for the editor. */
  public DraftContext loadDraftContext(long datasetId) {
    DatasetRepository.DraftSource source = repository.loadDraftSource(datasetId);
    List<DatasetFieldSpec> fields = repository.loadDraftFields(datasetId);
    return new DraftContext(
        source != null ? source.dataSourceId() : null,
        source != null ? source.sql() : null,
        fields);
  }

  public List<DatasetFieldSpec> preview(String dataSourceId, String sql) {
    return discovery.preview(requireDataSourceId(dataSourceId), requireSql(sql));
  }

  public DatasetSchemaDiscovery.QueryPreview previewQuery(String dataSourceId, String sql) {
    return discovery.previewQuery(requireDataSourceId(dataSourceId), requireSql(sql));
  }

  /** Save draft configuration (dataSource, SQL, fields) without creating an immutable version. */
  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail saveSqlQuery(
      long developmentNodeId,
      String dataSourceId,
      String sql,
      String name,
      String description,
      List<DatasetFieldSpec> requestedFields) {
    requireDevelopmentNodeId(developmentNodeId);
    String sourceId = requireDataSourceId(dataSourceId);
    String sourceSql = requireSql(sql);
    String normalizedName = required(name, "Dataset 名称", 200);
    String normalizedDescription = normalizeDescription(description);

    Dataset dataset = repository.findDatasetByDevelopmentNodeId(developmentNodeId).orElse(null);
    boolean newDataset = dataset == null;
    long datasetId;
    if (newDataset) {
      datasetId =
          repository.insertDevelopmentNodeDataset(
              developmentNodeId, normalizedName, normalizedDescription);
    } else {
      datasetId = dataset.id();
      repository.updateMetadata(datasetId, normalizedName, normalizedDescription);
    }

    List<DatasetFieldSpec> fields =
        requestedFields == null || requestedFields.isEmpty()
            ? fieldNormalizer.normalize(
                datasetId, discovery.discover(datasetId, sourceId, sourceSql))
            : fieldNormalizer.normalize(datasetId, requestedFields);

    // Save draft: only persist draft columns and draft fields, do NOT create a version.
    repository.updateDraft(datasetId, sourceId, sourceSql);
    repository.saveDraftFields(datasetId, fields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  /** Publish a new immutable version from the current draft (dataSource + SQL + fields). */
  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publishVersion(long developmentNodeId) {
    requireDevelopmentNodeId(developmentNodeId);
    DatasetDetail current = reader.findByDevelopmentNodeId(developmentNodeId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Dataset 尚未创建，请先保存：developmentNodeId=" + developmentNodeId));
    long datasetId = current.dataset().id();

    // Read draft fields as the field contract for the new version.
    List<DatasetFieldSpec> fields = repository.loadDraftFields(datasetId);
    if (fields.isEmpty()) {
      fields = current.fields().stream()
          .map(f -> new DatasetFieldSpec(
              f.fieldId(), f.physicalName(), f.displayName(),
              f.dataType(), f.nullable(), f.description(), f.defaultRole()))
          .toList();
    }
    fields = fieldNormalizer.normalize(datasetId, fields);

    // Resolve the SQL source from the draft columns saved by the editor.
    DatasetRepository.DraftSource draftSource = repository.loadDraftSource(datasetId);
    String draftDataSourceId = draftSource.dataSourceId();
    String draftSql = draftSource.sql();
    if (draftDataSourceId == null || draftDataSourceId.isBlank() || draftSql == null || draftSql.isBlank()) {
      throw new IllegalArgumentException(
          "Dataset 缺少有效的数据源或 SQL 配置，请先保存草稿：datasetId=" + datasetId);
    }

    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion != null
        && currentVersion.sourceType() == DatasetSourceType.SQL_QUERY
        && Objects.equals(currentVersion.dataSourceId(), draftDataSourceId)
        && Objects.equals(currentVersion.sql(), draftSql)
        && fieldNormalizer.sameFields(current.fields(), fields)) {
      // No material change since last version; skip version creation.
      lineagePublisher.request(datasetId);
      return current;
    }

    boolean hasExistingVersion = currentVersion != null;
    if (!hasExistingVersion) {
      versionWriter.appendInitialSqlQuery(
          datasetId, draftDataSourceId, draftSql, fields);
    } else {
      versionWriter.appendNextSqlQuery(
          datasetId, draftDataSourceId, draftSql, fields);
    }
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  public List<DatasetFieldSpec> previewTaskAsset(long sourceTaskAssetId) {
    return publisher.previewReleaseFields(sourceTaskAssetId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail saveTaskAsset(
      long developmentNodeId, DatasetPublishCommand command) {
    requireDevelopmentNodeId(developmentNodeId);
    DatasetTaskAssetSnapshot asset = publisher.requirePublishableAsset(command.sourceTaskAssetId());
    String name = normalizeName(command.name(), asset.name());
    String description = normalizeDescription(command.description());

    Optional<Dataset> existing = repository.findDatasetByDevelopmentNodeId(developmentNodeId);
    if (existing.isEmpty()) {
      long datasetId = repository.insertDevelopmentNodeDataset(developmentNodeId, name, description);
      List<DatasetFieldSpec> fields = resolveTaskFields(datasetId, asset, command.fields());
      versionWriter.appendInitialQueryRevision(
          datasetId,
          asset.id(),
          asset.currentRevisionId(),
          asset.currentRevisionNo(),
          fields);
      lineagePublisher.request(datasetId);
      return reader.require(datasetId);
    }

    long datasetId = existing.get().id();
    repository.updateMetadata(datasetId, name, description);
    DatasetDetail current = reader.require(datasetId);
    List<DatasetFieldSpec> fields = resolveTaskFields(datasetId, asset, command.fields());
    DatasetVersion version = current.currentVersion();
    if (version != null
        && version.sourceTaskAssetId() == asset.id()
        && version.sourceTaskRevisionId() == asset.currentRevisionId()
        && fieldNormalizer.sameFields(current.fields(), fields)) {
      lineagePublisher.request(datasetId);
      return reader.require(datasetId);
    }

    versionWriter.appendNextQueryRevision(
        datasetId,
        asset.id(),
        asset.currentRevisionId(),
        asset.currentRevisionNo(),
        fields);
    lineagePublisher.request(datasetId);
    return reader.require(datasetId);
  }

  private List<DatasetFieldSpec> resolveTaskFields(
      long datasetId,
      DatasetTaskAssetSnapshot asset,
      List<DatasetFieldSpec> requestedFields) {
    if (requestedFields != null && !requestedFields.isEmpty()) {
      return fieldNormalizer.normalize(datasetId, requestedFields);
    }
    return fieldNormalizer.normalize(datasetId, discovery.discover(datasetId, asset));
  }

  private void requireDevelopmentNodeId(long developmentNodeId) {
    if (developmentNodeId <= 0L) {
      throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    }
  }

  private String requireDataSourceId(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Dataset 必须选择数据源");
    }
    String normalized = value.trim();
    if (normalized.length() > 128) {
      throw new IllegalArgumentException("dataSourceId 不能超过 128 个字符");
    }
    return normalized;
  }

  private String requireSql(String value) {
    return DatasetSqlSafety.requireReadOnlyQuery(value);
  }

  private String normalizeName(String value, String fallback) {
    return required(value == null || value.isBlank() ? fallback : value, "Dataset 名称", 200);
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

  private String required(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " 不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }
}
