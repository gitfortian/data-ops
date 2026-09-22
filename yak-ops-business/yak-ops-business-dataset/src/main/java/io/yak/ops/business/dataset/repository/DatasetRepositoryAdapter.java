package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldDefinition;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.DatasetVersionDraft;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetDraftFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.business.dataset.repository.support.DatasetJsonCodec;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** Dataset aggregate persistence adapter. Domain/PO conversion is isolated here. */
@Repository
public class DatasetRepositoryAdapter implements DatasetRepository {

  private final DatasetDao datasetDao;
  private final DatasetJsonCodec jsonCodec;
  private final CurrentProject currentProject;

  @Autowired
  public DatasetRepositoryAdapter(
      DatasetDao datasetDao, DatasetJsonCodec jsonCodec, CurrentProject currentProject) {
    this.datasetDao = datasetDao;
    this.jsonCodec = jsonCodec;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations fail closed. */
  public DatasetRepositoryAdapter(DatasetDao datasetDao, DatasetJsonCodec jsonCodec) {
    this(datasetDao, jsonCodec, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public long insertDataset(String name, String description) {
    return insertDataset(null, name, description);
  }

  @Override
  public long insertDevelopmentNodeDataset(long developmentNodeId, String name, String description) {
    if (developmentNodeId <= 0L) throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    return insertDataset(developmentNodeId, name, description);
  }

  private long insertDataset(Long developmentNodeId, String name, String description) {
    DatasetPO po = new DatasetPO();
    po.setProjectId(requiredProjectId());
    po.setDevelopmentNodeId(developmentNodeId);
    po.setName(name);
    po.setDescription(description);
    po.setStatus(DatasetStatus.ONLINE.name());
    if (datasetDao.insertDataset(po) != 1 || po.getId() == null) {
      throw new IllegalStateException("创建 Dataset 后未返回主键");
    }
    return po.getId();
  }

  @Override
  public long appendVersion(DatasetVersionDraft draft) {
    if (draft == null) throw new IllegalArgumentException("DatasetVersionDraft 不能为空");
    Long projectId = requiredProjectId();
    if (datasetDao.selectDataset(projectId, draft.datasetId()) == null) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    DatasetVersionPO version = new DatasetVersionPO();
    version.setDatasetId(draft.datasetId());
    version.setVersionNo(draft.versionNo());
    version.setSourceType(draft.sourceType().name());
    version.setSourceTaskAssetId(draft.sourceTaskAssetId());
    version.setSourceTaskRevisionId(draft.sourceTaskRevisionId());
    version.setSourceTaskRevisionNo(draft.sourceTaskRevisionNo());
    version.setDataSourceId(draft.dataSourceId());
    version.setSqlContent(draft.sql());
    version.setSchemaSnapshot(jsonCodec.schemaSnapshot(draft.fields()));
    if (datasetDao.insertVersion(version) != 1 || version.getId() == null) {
      throw new IllegalStateException("创建 DatasetVersion 后未返回主键");
    }

    long versionId = version.getId();
    List<DatasetFieldPO> rows = new ArrayList<>(draft.fields().size());
    for (int index = 0; index < draft.fields().size(); index++) {
      DatasetFieldDefinition field = draft.fields().get(index);
      DatasetFieldPO po = new DatasetFieldPO();
      po.setFieldId(field.fieldId());
      po.setVersionId(versionId);
      po.setPhysicalName(field.physicalName());
      po.setDisplayName(field.displayName());
      po.setDataType(field.dataType().name());
      po.setNullable(field.nullable());
      po.setDescription(field.description());
      po.setDefaultRole(field.defaultRole().name());
      po.setSortOrder(index + 1);
      rows.add(po);
    }
    if (!rows.isEmpty()) {
      int affectedRows = datasetDao.insertFields(rows);
      if (affectedRows != rows.size()) {
        throw new IllegalStateException(
            "保存 Dataset 字段失败：expected=" + rows.size() + ", actual=" + affectedRows);
      }
    }
    return versionId;
  }

  @Override
  public void updateCurrentVersion(long datasetId, long versionId) {
    Long projectId = requiredProjectId();
    DatasetVersionPO version = datasetDao.selectVersion(projectId, versionId);
    if (version == null || version.getDatasetId() == null || version.getDatasetId() != datasetId) {
      throw new IllegalArgumentException(
          "DatasetVersion 不属于当前 Project 的 Dataset：datasetId="
              + datasetId
              + ", versionId="
              + versionId);
    }
    requireSingle(datasetDao.updateCurrentVersion(projectId, datasetId, versionId), datasetId);
  }

  @Override
  public void updateStatus(long datasetId, DatasetStatus status) {
    Long projectId = requiredProjectId();
    requireSingle(datasetDao.updateStatus(projectId, datasetId, status.name()), datasetId);
  }

  @Override
  public void updateMetadata(long datasetId, String name, String description) {
    Long projectId = requiredProjectId();
    requireSingle(datasetDao.updateMetadata(projectId, datasetId, name, description), datasetId);
  }

  @Override
  public Optional<Dataset> findDataset(long datasetId) {
    return Optional.ofNullable(datasetDao.selectDataset(requiredProjectId(), datasetId)).map(this::toDomain);
  }

  @Override
  public Optional<Dataset> findDatasetBySourceTaskAssetId(long sourceTaskAssetId) {
    return Optional.ofNullable(
            datasetDao.selectDatasetBySourceTaskAssetId(requiredProjectId(), sourceTaskAssetId))
        .map(this::toDomain);
  }

  @Override
  public Optional<Dataset> findDatasetByDevelopmentNodeId(long developmentNodeId) {
    return Optional.ofNullable(
            datasetDao.selectDatasetByDevelopmentNodeId(requiredProjectId(), developmentNodeId))
        .map(this::toDomain);
  }

  @Override
  public List<Dataset> listDatasets() {
    return datasetDao.selectDatasets(requiredProjectId()).stream().map(this::toDomain).toList();
  }

  @Override
  public List<Dataset> listDatasetsByIds(Collection<Long> datasetIds) {
    return datasetDao.selectDatasetsByIds(requiredProjectId(), datasetIds).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<DatasetVersion> findVersion(long versionId) {
    return Optional.ofNullable(datasetDao.selectVersion(requiredProjectId(), versionId))
        .map(this::toDomain);
  }

  @Override
  public Optional<DatasetVersion> findVersion(long datasetId, int versionNo) {
    return Optional.ofNullable(datasetDao.selectVersion(requiredProjectId(), datasetId, versionNo))
        .map(this::toDomain);
  }

  @Override
  public List<DatasetVersion> listVersions(long datasetId) {
    return datasetDao.selectVersions(requiredProjectId(), datasetId).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<DatasetVersion> listVersionsByIds(Collection<Long> versionIds) {
    return datasetDao.selectVersionsByIds(requiredProjectId(), versionIds).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<DatasetField> listFields(long versionId) {
    return datasetDao.selectFields(requiredProjectId(), versionId).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<DatasetField> listFieldsByVersionIds(Collection<Long> versionIds) {
    return datasetDao.selectFieldsByVersionIds(requiredProjectId(), versionIds).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public int nextVersionNo(long datasetId) {
    return datasetDao.selectNextVersionNo(requiredProjectId(), datasetId);
  }

  @Override
  public void updateDraft(long datasetId, String draftDataSourceId, String draftSql) {
    Long projectId = requiredProjectId();
    requireSingle(
        datasetDao.updateDraft(projectId, datasetId, draftDataSourceId, draftSql, null),
        datasetId);
  }

  @Override
  public DraftSource loadDraftSource(long datasetId) {
    Long projectId = requiredProjectId();
    DatasetPO po = datasetDao.selectDataset(projectId, datasetId);
    if (po == null) throw new IllegalArgumentException("Dataset 不存在：" + datasetId);
    return new DraftSource(po.getDraftDataSourceId(), po.getDraftSql());
  }

  @Override
  public void saveDraftFields(long datasetId, List<DatasetFieldSpec> fields) {
    Long projectId = requiredProjectId();
    datasetDao.deleteDraftFields(projectId, datasetId);
    if (fields == null || fields.isEmpty()) return;
    List<DatasetDraftFieldPO> rows = new ArrayList<>(fields.size());
    for (int index = 0; index < fields.size(); index++) {
      DatasetFieldSpec spec = fields.get(index);
      DatasetDraftFieldPO po = new DatasetDraftFieldPO();
      po.setFieldId(spec.fieldId());
      po.setDatasetId(datasetId);
      po.setPhysicalName(spec.physicalName());
      po.setDisplayName(spec.displayName());
      po.setDataType(spec.dataType().name());
      po.setNullable(spec.nullable());
      po.setDescription(spec.description());
      po.setDefaultRole(spec.defaultRole().name());
      po.setSortOrder(index + 1);
      rows.add(po);
    }
    int affectedRows = datasetDao.insertDraftFields(rows);
    if (affectedRows != rows.size()) {
      throw new IllegalStateException(
          "保存 Dataset 草稿字段失败：expected=" + rows.size() + ", actual=" + affectedRows);
    }
  }

  @Override
  public List<DatasetFieldSpec> loadDraftFields(long datasetId) {
    Long projectId = requiredProjectId();
    return datasetDao.selectDraftFields(projectId, datasetId).stream()
        .map(this::toFieldSpec)
        .toList();
  }

  private DatasetFieldSpec toFieldSpec(DatasetDraftFieldPO po) {
    return new DatasetFieldSpec(
        po.getFieldId(),
        po.getPhysicalName(),
        po.getDisplayName(),
        DatasetFieldDataType.valueOf(po.getDataType()),
        Boolean.TRUE.equals(po.getNullable()),
        po.getDescription(),
        DatasetFieldRole.valueOf(po.getDefaultRole()));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private Dataset toDomain(DatasetPO po) {
    return new Dataset(
        po.getId(),
        po.getProjectId(),
        po.getName(),
        po.getDescription(),
        DatasetStatus.valueOf(po.getStatus()),
        po.getCurrentVersionId(),
        instant(po.getCreateTime()),
        instant(po.getUpdateTime()));
  }

  private DatasetVersion toDomain(DatasetVersionPO po) {
    return new DatasetVersion(
        po.getId(),
        po.getDatasetId(),
        po.getVersionNo(),
        DatasetSourceType.valueOf(po.getSourceType()),
        po.getSourceTaskAssetId(),
        po.getSourceTaskRevisionId(),
        po.getSourceTaskRevisionNo(),
        po.getDataSourceId(),
        po.getSqlContent(),
        po.getSchemaSnapshot(),
        instant(po.getCreateTime()));
  }

  private DatasetField toDomain(DatasetFieldPO po) {
    return new DatasetField(
        po.getFieldId(),
        po.getVersionId(),
        po.getPhysicalName(),
        po.getDisplayName(),
        DatasetFieldDataType.valueOf(po.getDataType()),
        Boolean.TRUE.equals(po.getNullable()),
        po.getDescription(),
        DatasetFieldRole.valueOf(po.getDefaultRole()),
        po.getSortOrder());
  }

  private void requireSingle(int affectedRows, long datasetId) {
    if (affectedRows != 1) throw new IllegalArgumentException("Dataset 不存在：" + datasetId);
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
