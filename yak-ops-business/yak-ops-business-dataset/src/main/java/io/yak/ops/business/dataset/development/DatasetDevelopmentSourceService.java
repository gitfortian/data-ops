package io.yak.ops.business.dataset.development;

import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only provenance from stable Dataset identity back to its Development owner, when applicable. */
@Service
@RequiredArgsConstructor
public class DatasetDevelopmentSourceService {

  private final DatasetDao datasetDao;
  private final CurrentProject currentProject;

  public DevelopmentSource require(long datasetId) {
    if (datasetId <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
    Long projectId = currentProject.requireProjectId();
    DatasetPO dataset = datasetDao.selectDataset(projectId, datasetId);
    if (dataset == null) {
      throw new IllegalArgumentException("当前 Project 下 Dataset 不存在：" + datasetId);
    }
    Long nodeId = dataset.getDevelopmentNodeId();
    if (nodeId == null || nodeId <= 0L) {
      return new DevelopmentSource(datasetId, null, null, "NOT_APPLICABLE");
    }
    Long currentVersionId = dataset.getCurrentVersionId();
    DatasetVersionPO currentVersion = currentVersionId == null
        ? null
        : datasetDao.selectVersion(projectId, currentVersionId);
    Integer versionNo = currentVersion == null ? null : currentVersion.getVersionNo();
    return new DevelopmentSource(datasetId, nodeId, versionNo, "FOUND");
  }

  public record DevelopmentSource(
      long datasetId,
      Long developmentNodeId,
      Integer currentDatasetVersionNo,
      String state) {}
}
