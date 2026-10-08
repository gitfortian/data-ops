package io.yak.ops.business.consumption.relationship.source;

import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceReader;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Retryable projection from persistent Dataset query diagnostics into Consumption Usage Evidence. */
@Service
@RequiredArgsConstructor
public class DatasetUsageEvidenceSynchronizer {

  private final DatasetQueryPerformanceReader performanceReader;
  private final DatasetUsageEvidenceNormalizer normalizer;
  private final CurrentProject currentProject;

  public List<UsageNormalizationResult> synchronizeRecent(int limit) {
    return synchronizeRecent(Set.of(), limit);
  }

  public List<UsageNormalizationResult> synchronizeRecentByProduct(long datasetId, int limit) {
    return synchronizeRecent(Set.of(datasetId), limit);
  }

  private List<UsageNormalizationResult> synchronizeRecent(Set<Long> datasetIds, int limit) {
    Long projectId = currentProject.requireProjectId();
    int boundedLimit = Math.max(1, Math.min(200, limit));
    return performanceReader.recentPersisted(
            datasetIds, Set.of(DatasetQueryStatus.SUCCESS), boundedLimit).stream()
        .map(trace -> normalizer.normalize(projectId, trace))
        .toList();
  }
}
