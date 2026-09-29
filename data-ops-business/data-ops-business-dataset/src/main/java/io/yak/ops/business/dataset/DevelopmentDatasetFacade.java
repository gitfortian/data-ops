package io.yak.ops.business.dataset;

import io.yak.ops.business.dataset.development.DevelopmentDatasetManager;
import io.yak.ops.business.dataset.development.DevelopmentDatasetManager.DraftContext;
import io.yak.ops.business.dataset.publication.DatasetPublishCommand;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Stable public boundary used by Data Development Dataset nodes. */
@Service
public class DevelopmentDatasetFacade {

  private final DevelopmentDatasetManager manager;

  public DevelopmentDatasetFacade(DevelopmentDatasetManager manager) {
    this.manager = manager;
  }

  public Optional<NodeDataset> findByDevelopmentNodeId(long developmentNodeId) {
    return manager.find(developmentNodeId).map(detail -> {
      DraftContext draft = tryLoadDraft(detail.dataset().id());
      return toNodeDataset(developmentNodeId, detail, draft);
    });
  }

  /** Standalone Dataset editor preview: datasource + SQL belong to Dataset itself. */
  public List<FieldDraft> preview(String dataSourceId, String sql) {
    return manager.preview(dataSourceId, sql).stream()
        .map(DevelopmentDatasetFacade::toFieldDraft)
        .toList();
  }

  /** Execute Dataset-owned SQL for the editor and expose both field metadata and real rows. */
  public PreviewResult previewQuery(String dataSourceId, String sql) {
    DatasetSchemaDiscovery.QueryPreview preview = manager.previewQuery(dataSourceId, sql);
    return new PreviewResult(
        preview.fields().stream().map(DevelopmentDatasetFacade::toFieldDraft).toList(),
        preview.result().columns().stream()
            .map(
                column ->
                    new PreviewColumn(
                        column.name(),
                        column.label(),
                        column.typeName(),
                        column.jdbcType(),
                        column.nullable()))
            .toList(),
        preview.result().rows(),
        preview.result().rows().size(),
        preview.result().truncated(),
        preview.durationMs());
  }

  public NodeDataset save(
      long developmentNodeId,
      String dataSourceId,
      String sql,
      String name,
      String description,
      List<FieldDraft> fields) {
    List<DatasetFieldSpec> specs =
        fields == null
            ? List.of()
            : fields.stream().map(DevelopmentDatasetFacade::toFieldSpec).toList();
    DatasetDetail detail =
        manager.saveSqlQuery(developmentNodeId, dataSourceId, sql, name, description, specs);
    DraftContext draft = new DraftContext(dataSourceId, sql, specs);
    return toNodeDataset(developmentNodeId, detail, draft);
  }

  /** Legacy TaskAsset API retained for release flows/tests outside the Dataset node editor. */
  public List<FieldDraft> preview(long sourceTaskAssetId) {
    return manager.previewTaskAsset(sourceTaskAssetId).stream()
        .map(DevelopmentDatasetFacade::toFieldDraft)
        .toList();
  }

  /** Legacy TaskAsset API retained for source compatibility. */
  public NodeDataset save(
      long developmentNodeId,
      long sourceTaskAssetId,
      String name,
      String description,
      List<FieldDraft> fields) {
    List<DatasetFieldSpec> specs =
        fields == null
            ? List.of()
            : fields.stream().map(DevelopmentDatasetFacade::toFieldSpec).toList();
    DatasetDetail detail =
        manager.saveTaskAsset(
            developmentNodeId,
            new DatasetPublishCommand(sourceTaskAssetId, name, description, specs));
    return toNodeDataset(developmentNodeId, detail, null);
  }

  /** Publish a new immutable DatasetVersion from the current draft. */
  public NodeDataset publishVersion(long developmentNodeId) {
    DatasetDetail detail = manager.publishVersion(developmentNodeId);
    return toNodeDataset(developmentNodeId, detail, null);
  }

  private NodeDataset toNodeDataset(
      long developmentNodeId,
      DatasetDetail detail,
      DraftContext draft) {
    Dataset dataset = detail.dataset();
    VersionSnapshot currentVersion =
        detail.currentVersion() == null ? null : toVersion(detail.currentVersion());

    // Draft takes priority over version: the editor should reflect the last saved work,
    // even if no immutable version has been published yet.
    String draftDataSourceId = draft != null ? draft.dataSourceId() : null;
    String draftSql = draft != null ? draft.sql() : null;
    List<FieldDraft> draftFields =
        draft != null && !draft.fields().isEmpty()
            ? draft.fields().stream().map(DevelopmentDatasetFacade::toFieldDraft).toList()
            : null;

    return new NodeDataset(
        String.valueOf(developmentNodeId),
        String.valueOf(dataset.id()),
        dataset.requireProjectId(),
        dataset.name(),
        dataset.description(),
        dataset.status().name(),
        currentVersion,
        detail.versions().stream().map(DevelopmentDatasetFacade::toVersion).toList(),
        detail.fields().stream().map(DevelopmentDatasetFacade::toField).toList(),
        draftDataSourceId,
        draftSql,
        draftFields,
        dataset.createTime(),
        dataset.updateTime());
  }

  private static VersionSnapshot toVersion(DatasetVersion version) {
    return new VersionSnapshot(
        String.valueOf(version.id()),
        version.versionNo(),
        version.sourceType().name(),
        String.valueOf(version.sourceTaskAssetId()),
        String.valueOf(version.sourceTaskRevisionId()),
        version.sourceTaskRevisionNo(),
        version.dataSourceId(),
        version.sql(),
        version.createTime());
  }

  private static FieldSnapshot toField(DatasetField field) {
    return new FieldSnapshot(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        field.dataType().name(),
        field.nullable(),
        field.description(),
        field.defaultRole().name(),
        field.sortOrder());
  }

  private static FieldDraft toFieldDraft(DatasetFieldSpec field) {
    return new FieldDraft(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        field.dataType().name(),
        field.nullable(),
        field.description(),
        field.defaultRole().name());
  }

  private static DatasetFieldSpec toFieldSpec(FieldDraft field) {
    if (field == null) {
      throw new IllegalArgumentException("Dataset 字段不能为空");
    }
    return new DatasetFieldSpec(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        parseDataType(field.dataType()),
        field.nullable(),
        field.description(),
        parseRole(field.defaultRole()));
  }

  private static DatasetFieldDataType parseDataType(String value) {
    if (value == null || value.isBlank()) {
      return DatasetFieldDataType.UNKNOWN;
    }
    try {
      return DatasetFieldDataType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 Dataset 字段类型：" + value, exception);
    }
  }

  private static DatasetFieldRole parseRole(String value) {
    if (value == null || value.isBlank()) {
      return DatasetFieldRole.DIMENSION;
    }
    try {
      return DatasetFieldRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 Dataset 字段角色：" + value, exception);
    }
  }

  private DraftContext tryLoadDraft(long datasetId) {
    try {
      return manager.loadDraftContext(datasetId);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  public record NodeDataset(
      String developmentNodeId,
      String datasetId,
      long projectId,
      String name,
      String description,
      String status,
      VersionSnapshot currentVersion,
      List<VersionSnapshot> versions,
      List<FieldSnapshot> fields,
      String draftDataSourceId,
      String draftSql,
      List<FieldDraft> draftFields,
      Instant createTime,
      Instant updateTime) {

    /** Compatibility constructor for callers that do not provide a Project-aware snapshot yet. */
    public NodeDataset(
        String developmentNodeId,
        String datasetId,
        String name,
        String description,
        String status,
        VersionSnapshot currentVersion,
        List<VersionSnapshot> versions,
        List<FieldSnapshot> fields,
        Instant createTime,
        Instant updateTime) {
      this(
          developmentNodeId,
          datasetId,
          0L,
          name,
          description,
          status,
          currentVersion,
          versions,
          fields,
          null,
          null,
          null,
          createTime,
          updateTime);
    }
  }

  public record VersionSnapshot(
      String versionId,
      int versionNo,
      String sourceType,
      String sourceTaskAssetId,
      String sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String dataSourceId,
      String sql,
      Instant createTime) {

    public VersionSnapshot(
        String versionId,
        int versionNo,
        String sourceType,
        String sourceTaskAssetId,
        String sourceTaskRevisionId,
        int sourceTaskRevisionNo,
        Instant createTime) {
      this(
          versionId,
          versionNo,
          sourceType,
          sourceTaskAssetId,
          sourceTaskRevisionId,
          sourceTaskRevisionNo,
          null,
          null,
          createTime);
    }
  }

  public record FieldSnapshot(
      String fieldId,
      String physicalName,
      String displayName,
      String dataType,
      boolean nullable,
      String description,
      String defaultRole,
      int sortOrder) {}

  public record FieldDraft(
      String fieldId,
      String physicalName,
      String displayName,
      String dataType,
      boolean nullable,
      String description,
      String defaultRole) {}

  public record PreviewColumn(
      String name, String label, String typeName, int jdbcType, boolean nullable) {}

  public record PreviewResult(
      List<FieldDraft> fields,
      List<PreviewColumn> columns,
      List<List<Object>> rows,
      int returnedRows,
      boolean truncated,
      long durationMs) {
    public PreviewResult {
      fields = fields == null ? List.of() : List.copyOf(fields);
      columns = columns == null ? List.of() : List.copyOf(columns);
      rows = rows == null ? List.of() : List.copyOf(rows);
    }
  }
}
