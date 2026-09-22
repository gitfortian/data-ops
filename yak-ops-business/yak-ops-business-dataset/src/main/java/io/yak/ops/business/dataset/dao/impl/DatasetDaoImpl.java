package io.yak.ops.business.dataset.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.mapper.DatasetDraftFieldMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetFieldMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetQueryPerformanceMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetVersionMapper;
import io.yak.ops.business.dataset.dao.model.DatasetDraftFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetQueryPerformancePO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus implementation for Dataset persistence primitives. */
@Repository
@DependsOn("yakDatasetFlyway")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DatasetDaoImpl implements DatasetDao {

  private static final int FIELD_INSERT_BATCH_SIZE = 200;
  private static final int MAX_QUERY_PERFORMANCE_LIMIT = 200;
  private static final int MAX_QUERY_PERFORMANCE_CLEANUP_BATCH = 5000;
  private static final int DRAFT_FIELD_INSERT_BATCH_SIZE = 200;

  private final DatasetMapper datasetMapper;
  private final DatasetVersionMapper versionMapper;
  private final DatasetFieldMapper fieldMapper;
  private final DatasetDraftFieldMapper draftFieldMapper;
  private final DatasetQueryPerformanceMapper queryPerformanceMapper;

  @Override
  public int insertDataset(DatasetPO dataset) {
    Timestamp now = Timestamp.from(Instant.now());
    dataset.setCreateTime(now);
    dataset.setUpdateTime(now);
    return datasetMapper.insert(dataset);
  }

  @Override
  public int insertVersion(DatasetVersionPO version) {
    version.setCreateTime(Timestamp.from(Instant.now()));
    return versionMapper.insert(version);
  }

  @Override
  public int insertField(DatasetFieldPO field) {
    return fieldMapper.insert(field);
  }

  @Override
  public int insertFields(List<DatasetFieldPO> fields) {
    if (fields == null || fields.isEmpty()) {
      return 0;
    }
    int affectedRows = 0;
    for (int start = 0; start < fields.size(); start += FIELD_INSERT_BATCH_SIZE) {
      int end = Math.min(start + FIELD_INSERT_BATCH_SIZE, fields.size());
      affectedRows += fieldMapper.insertBatch(fields.subList(start, end));
    }
    return affectedRows;
  }

  @Override
  public int updateCurrentVersion(long datasetId, long versionId) {
    return updateCurrentVersion(null, datasetId, versionId);
  }

  @Override
  public int updateCurrentVersion(Long projectId, long datasetId, long versionId) {
    return datasetMapper.update(
        null,
        Wrappers.<DatasetPO>lambdaUpdate()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .eq(DatasetPO::getId, datasetId)
            .set(DatasetPO::getCurrentVersionId, versionId)
            .set(DatasetPO::getUpdateTime, Timestamp.from(Instant.now())));
  }

  @Override
  public int updateStatus(long datasetId, String status) {
    return updateStatus(null, datasetId, status);
  }

  @Override
  public int updateStatus(Long projectId, long datasetId, String status) {
    return datasetMapper.update(
        null,
        Wrappers.<DatasetPO>lambdaUpdate()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .eq(DatasetPO::getId, datasetId)
            .set(DatasetPO::getStatus, status)
            .set(DatasetPO::getUpdateTime, Timestamp.from(Instant.now())));
  }

  @Override
  public int updateMetadata(long datasetId, String name, String description) {
    return updateMetadata(null, datasetId, name, description);
  }

  @Override
  public int updateMetadata(
      Long projectId, long datasetId, String name, String description) {
    return datasetMapper.update(
        null,
        Wrappers.<DatasetPO>lambdaUpdate()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .eq(DatasetPO::getId, datasetId)
            .set(DatasetPO::getName, name)
            .set(DatasetPO::getDescription, description)
            .set(DatasetPO::getUpdateTime, Timestamp.from(Instant.now())));
  }

  @Override
  public DatasetPO selectDataset(long datasetId) {
    return selectDataset(null, datasetId);
  }

  @Override
  public DatasetPO selectDataset(Long projectId, long datasetId) {
    if (projectId == null) return datasetMapper.selectById(datasetId);
    return datasetMapper.selectOne(
        Wrappers.<DatasetPO>lambdaQuery()
            .eq(DatasetPO::getProjectId, projectId)
            .eq(DatasetPO::getId, datasetId));
  }

  @Override
  public DatasetPO selectDatasetBySourceTaskAssetId(long sourceTaskAssetId) {
    return selectDatasetBySourceTaskAssetId(null, sourceTaskAssetId);
  }

  @Override
  public DatasetPO selectDatasetBySourceTaskAssetId(Long projectId, long sourceTaskAssetId) {
    List<Object> values = versionMapper.selectObjs(
        Wrappers.<DatasetVersionPO>query()
            .select("dataset_id")
            .eq("source_task_asset_id", sourceTaskAssetId)
            .orderByDesc("id"));
    List<Long> datasetIds = values.stream()
        .filter(value -> value instanceof Number)
        .map(value -> ((Number) value).longValue())
        .distinct()
        .toList();
    if (datasetIds.isEmpty()) return null;
    return datasetMapper.selectList(
        Wrappers.<DatasetPO>lambdaQuery()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .in(DatasetPO::getId, datasetIds)
            .isNull(DatasetPO::getDevelopmentNodeId)
            .orderByDesc(DatasetPO::getUpdateTime)
            .orderByDesc(DatasetPO::getId))
        .stream()
        .findFirst()
        .orElse(null);
  }

  @Override
  public DatasetPO selectDatasetByDevelopmentNodeId(long developmentNodeId) {
    return selectDatasetByDevelopmentNodeId(null, developmentNodeId);
  }

  @Override
  public DatasetPO selectDatasetByDevelopmentNodeId(Long projectId, long developmentNodeId) {
    return datasetMapper.selectOne(
        Wrappers.<DatasetPO>lambdaQuery()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .eq(DatasetPO::getDevelopmentNodeId, developmentNodeId));
  }

  @Override
  public List<DatasetPO> selectDatasets() {
    return selectDatasets(null);
  }

  @Override
  public List<DatasetPO> selectDatasets(Long projectId) {
    return datasetMapper.selectList(
        Wrappers.<DatasetPO>lambdaQuery()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .orderByDesc(DatasetPO::getUpdateTime)
            .orderByDesc(DatasetPO::getId));
  }

  @Override
  public List<DatasetPO> selectDatasetsByIds(Long projectId, Collection<Long> datasetIds) {
    if (datasetIds == null || datasetIds.isEmpty()) {
      return List.of();
    }
    return datasetMapper.selectList(
        Wrappers.<DatasetPO>lambdaQuery()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .in(DatasetPO::getId, datasetIds)
            .orderByDesc(DatasetPO::getUpdateTime)
            .orderByDesc(DatasetPO::getId));
  }

  @Override
  public DatasetVersionPO selectVersion(long versionId) {
    return versionMapper.selectById(versionId);
  }

  @Override
  public DatasetVersionPO selectVersion(Long projectId, long versionId) {
    return projectId == null
        ? selectVersion(versionId)
        : versionMapper.selectProjectVersion(projectId, versionId);
  }

  @Override
  public DatasetVersionPO selectVersion(long datasetId, int versionNo) {
    return versionMapper.selectOne(
        Wrappers.<DatasetVersionPO>lambdaQuery()
            .eq(DatasetVersionPO::getDatasetId, datasetId)
            .eq(DatasetVersionPO::getVersionNo, versionNo));
  }

  @Override
  public DatasetVersionPO selectVersion(Long projectId, long datasetId, int versionNo) {
    return projectId == null
        ? selectVersion(datasetId, versionNo)
        : versionMapper.selectProjectVersionNo(projectId, datasetId, versionNo);
  }

  @Override
  public List<DatasetVersionPO> selectVersions(long datasetId) {
    return versionMapper.selectList(
        Wrappers.<DatasetVersionPO>lambdaQuery()
            .eq(DatasetVersionPO::getDatasetId, datasetId)
            .orderByDesc(DatasetVersionPO::getVersionNo));
  }

  @Override
  public List<DatasetVersionPO> selectVersions(Long projectId, long datasetId) {
    return projectId == null
        ? selectVersions(datasetId)
        : versionMapper.selectProjectVersions(projectId, datasetId);
  }

  @Override
  public List<DatasetVersionPO> selectVersionsByIds(Collection<Long> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) {
      return List.of();
    }
    return versionMapper.selectList(
        Wrappers.<DatasetVersionPO>lambdaQuery()
            .in(DatasetVersionPO::getId, versionIds));
  }

  @Override
  public List<DatasetVersionPO> selectVersionsByIds(
      Long projectId, Collection<Long> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) {
      return List.of();
    }
    return projectId == null
        ? selectVersionsByIds(versionIds)
        : versionMapper.selectProjectVersionsByIds(projectId, versionIds);
  }

  @Override
  public List<DatasetFieldPO> selectFields(long versionId) {
    return fieldMapper.selectList(
        Wrappers.<DatasetFieldPO>lambdaQuery()
            .eq(DatasetFieldPO::getVersionId, versionId)
            .orderByAsc(DatasetFieldPO::getSortOrder)
            .orderByAsc(DatasetFieldPO::getPhysicalName));
  }

  @Override
  public List<DatasetFieldPO> selectFields(Long projectId, long versionId) {
    return projectId == null
        ? selectFields(versionId)
        : fieldMapper.selectProjectFields(projectId, versionId);
  }

  @Override
  public List<DatasetFieldPO> selectFieldsByVersionIds(Collection<Long> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) {
      return List.of();
    }
    return fieldMapper.selectList(
        Wrappers.<DatasetFieldPO>lambdaQuery()
            .in(DatasetFieldPO::getVersionId, versionIds)
            .orderByAsc(DatasetFieldPO::getVersionId)
            .orderByAsc(DatasetFieldPO::getSortOrder)
            .orderByAsc(DatasetFieldPO::getPhysicalName));
  }

  @Override
  public List<DatasetFieldPO> selectFieldsByVersionIds(
      Long projectId, Collection<Long> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) {
      return List.of();
    }
    return projectId == null
        ? selectFieldsByVersionIds(versionIds)
        : fieldMapper.selectProjectFieldsByVersionIds(projectId, versionIds);
  }

  @Override
  public int selectNextVersionNo(long datasetId) {
    List<Object> values = versionMapper.selectObjs(
        Wrappers.<DatasetVersionPO>query()
            .select("COALESCE(MAX(version_no), 0) + 1")
            .eq("dataset_id", datasetId));
    if (values == null || values.isEmpty() || values.get(0) == null) return 1;
    return ((Number) values.get(0)).intValue();
  }

  @Override
  public int selectNextVersionNo(Long projectId, long datasetId) {
    if (projectId == null) return selectNextVersionNo(datasetId);
    Integer value = versionMapper.selectProjectNextVersionNo(projectId, datasetId);
    return value == null ? 1 : value;
  }

  @Override
  public int insertQueryPerformance(DatasetQueryPerformancePO trace) {
    return queryPerformanceMapper.insert(trace);
  }

  @Override
  public List<DatasetQueryPerformancePO> selectQueryPerformance(
      Long projectId,
      Collection<Long> datasetIds,
      Collection<String> queryIds,
      Collection<String> statuses,
      Long minTotalMillis,
      int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_PERFORMANCE_LIMIT));
    var query = Wrappers.<DatasetQueryPerformancePO>lambdaQuery();
    if (projectId == null) {
      query.isNull(DatasetQueryPerformancePO::getProjectId);
    } else {
      query.eq(DatasetQueryPerformancePO::getProjectId, projectId);
    }
    query.in(datasetIds != null && !datasetIds.isEmpty(),
            DatasetQueryPerformancePO::getDatasetId, datasetIds)
        .in(queryIds != null && !queryIds.isEmpty(),
            DatasetQueryPerformancePO::getQueryId, queryIds)
        .in(statuses != null && !statuses.isEmpty(),
            DatasetQueryPerformancePO::getStatus, statuses)
        .ge(minTotalMillis != null,
            DatasetQueryPerformancePO::getTotalMillis,
            minTotalMillis == null ? 0L : Math.max(0L, minTotalMillis))
        .orderByDesc(DatasetQueryPerformancePO::getStartedAt)
        .orderByDesc(DatasetQueryPerformancePO::getId)
        .last("LIMIT " + limit);
    return queryPerformanceMapper.selectList(query);
  }

  @Override
  public int deleteQueryPerformanceBefore(Instant cutoff, int requestedLimit) {
    if (cutoff == null) return 0;
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_PERFORMANCE_CLEANUP_BATCH));
    return queryPerformanceMapper.deleteBefore(Timestamp.from(cutoff), limit);
  }

  // ---- Draft ----

  @Override
  public int updateDraft(long datasetId, String draftDataSourceId, String draftSql, String draftSchemaSnapshot) {
    return updateDraft(null, datasetId, draftDataSourceId, draftSql, draftSchemaSnapshot);
  }

  @Override
  public int updateDraft(Long projectId, long datasetId, String draftDataSourceId, String draftSql, String draftSchemaSnapshot) {
    return datasetMapper.update(
        null,
        Wrappers.<DatasetPO>lambdaUpdate()
            .eq(projectId != null, DatasetPO::getProjectId, projectId)
            .eq(DatasetPO::getId, datasetId)
            .set(DatasetPO::getDraftDataSourceId, draftDataSourceId)
            .set(DatasetPO::getDraftSql, draftSql)
            .set(DatasetPO::getDraftSchemaSnapshot, draftSchemaSnapshot)
            .set(DatasetPO::getUpdateTime, Timestamp.from(Instant.now())));
  }

  @Override
  public int deleteDraftFields(long datasetId) {
    return deleteDraftFields(null, datasetId);
  }

  @Override
  public int deleteDraftFields(Long projectId, long datasetId) {
    if (projectId != null) {
      DatasetPO dataset = datasetMapper.selectOne(
          Wrappers.<DatasetPO>lambdaQuery()
              .eq(DatasetPO::getProjectId, projectId)
              .eq(DatasetPO::getId, datasetId));
      if (dataset == null) return 0;
    }
    return draftFieldMapper.delete(
        Wrappers.<DatasetDraftFieldPO>lambdaQuery()
            .eq(DatasetDraftFieldPO::getDatasetId, datasetId));
  }

  @Override
  public int insertDraftFields(List<DatasetDraftFieldPO> fields) {
    if (fields == null || fields.isEmpty()) return 0;
    int affectedRows = 0;
    for (int start = 0; start < fields.size(); start += DRAFT_FIELD_INSERT_BATCH_SIZE) {
      int end = Math.min(start + DRAFT_FIELD_INSERT_BATCH_SIZE, fields.size());
      affectedRows += draftFieldMapper.insertBatch(fields.subList(start, end));
    }
    return affectedRows;
  }

  @Override
  public List<DatasetDraftFieldPO> selectDraftFields(long datasetId) {
    return selectDraftFields(null, datasetId);
  }

  @Override
  public List<DatasetDraftFieldPO> selectDraftFields(Long projectId, long datasetId) {
    if (projectId != null) {
      DatasetPO dataset = datasetMapper.selectOne(
          Wrappers.<DatasetPO>lambdaQuery()
              .eq(DatasetPO::getProjectId, projectId)
              .eq(DatasetPO::getId, datasetId));
      if (dataset == null) return List.of();
    }
    return draftFieldMapper.selectList(
        Wrappers.<DatasetDraftFieldPO>lambdaQuery()
            .eq(DatasetDraftFieldPO::getDatasetId, datasetId)
            .orderByAsc(DatasetDraftFieldPO::getSortOrder)
            .orderByAsc(DatasetDraftFieldPO::getPhysicalName));
  }
}
