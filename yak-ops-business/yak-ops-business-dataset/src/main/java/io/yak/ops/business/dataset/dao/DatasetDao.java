package io.yak.ops.business.dataset.dao;

import io.yak.ops.business.dataset.dao.model.DatasetDraftFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetQueryPerformancePO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Database access boundary. MyBatis/PO types stay below the Repository adapter. */
public interface DatasetDao {

  int insertDataset(DatasetPO dataset);

  int insertVersion(DatasetVersionPO version);

  int insertField(DatasetFieldPO field);

  int insertFields(List<DatasetFieldPO> fields);

  int updateCurrentVersion(long datasetId, long versionId);

  int updateCurrentVersion(Long projectId, long datasetId, long versionId);

  int updateStatus(long datasetId, String status);

  int updateStatus(Long projectId, long datasetId, String status);

  int updateMetadata(long datasetId, String name, String description);

  int updateMetadata(Long projectId, long datasetId, String name, String description);

  DatasetPO selectDataset(long datasetId);

  DatasetPO selectDataset(Long projectId, long datasetId);

  DatasetPO selectDatasetBySourceTaskAssetId(long sourceTaskAssetId);

  DatasetPO selectDatasetBySourceTaskAssetId(Long projectId, long sourceTaskAssetId);

  DatasetPO selectDatasetByDevelopmentNodeId(long developmentNodeId);

  DatasetPO selectDatasetByDevelopmentNodeId(Long projectId, long developmentNodeId);

  List<DatasetPO> selectDatasets();

  List<DatasetPO> selectDatasets(Long projectId);

  List<DatasetPO> selectDatasetsByIds(Long projectId, Collection<Long> datasetIds);

  DatasetVersionPO selectVersion(long versionId);

  DatasetVersionPO selectVersion(Long projectId, long versionId);

  DatasetVersionPO selectVersion(long datasetId, int versionNo);

  DatasetVersionPO selectVersion(Long projectId, long datasetId, int versionNo);

  List<DatasetVersionPO> selectVersions(long datasetId);

  List<DatasetVersionPO> selectVersions(Long projectId, long datasetId);

  List<DatasetVersionPO> selectVersionsByIds(Collection<Long> versionIds);

  List<DatasetVersionPO> selectVersionsByIds(Long projectId, Collection<Long> versionIds);

  List<DatasetFieldPO> selectFields(long versionId);

  List<DatasetFieldPO> selectFields(Long projectId, long versionId);

  List<DatasetFieldPO> selectFieldsByVersionIds(Collection<Long> versionIds);

  List<DatasetFieldPO> selectFieldsByVersionIds(Long projectId, Collection<Long> versionIds);

  int selectNextVersionNo(long datasetId);

  int selectNextVersionNo(Long projectId, long datasetId);

  int insertQueryPerformance(DatasetQueryPerformancePO trace);

  List<DatasetQueryPerformancePO> selectQueryPerformance(
      Long projectId,
      Collection<Long> datasetIds,
      Collection<String> queryIds,
      Collection<String> statuses,
      Long minTotalMillis,
      int limit);

  int deleteQueryPerformanceBefore(Instant cutoff, int limit);

  // ---- Draft columns & draft-field table ----

  int updateDraft(long datasetId, String draftDataSourceId, String draftSql, String draftSchemaSnapshot);

  int updateDraft(Long projectId, long datasetId, String draftDataSourceId, String draftSql, String draftSchemaSnapshot);

  int deleteDraftFields(long datasetId);

  int deleteDraftFields(Long projectId, long datasetId);

  int insertDraftFields(List<DatasetDraftFieldPO> fields);

  List<DatasetDraftFieldPO> selectDraftFields(long datasetId);

  List<DatasetDraftFieldPO> selectDraftFields(Long projectId, long datasetId);
}
