package io.yak.ops.business.dataset.development;

import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.repository.DatasetRepository.DevelopmentSourceDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read-only provenance from stable Dataset identity back to its Development owner, when applicable. */
@Component
@RequiredArgsConstructor
public class DatasetDevelopmentSourceService {

  private final DatasetRepository datasetRepository;

  public DevelopmentSource require(long datasetId) {
    if (datasetId <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
    DevelopmentSourceDetails details =
        datasetRepository
            .findDevelopmentSource(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("当前 Project 下 Dataset 不存在：" + datasetId));
    Long nodeId = details.developmentNodeId();
    if (nodeId == null || nodeId <= 0L) {
      return new DevelopmentSource(datasetId, null, null, "NOT_APPLICABLE");
    }
    return new DevelopmentSource(
        datasetId, nodeId, details.currentDatasetVersionNo(), "FOUND");
  }

  public record DevelopmentSource(
      long datasetId,
      Long developmentNodeId,
      Integer currentDatasetVersionNo,
      String state) {}
}
