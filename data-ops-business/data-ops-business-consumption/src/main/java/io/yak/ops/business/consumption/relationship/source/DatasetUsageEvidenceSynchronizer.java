package io.yak.ops.business.consumption.relationship.source;

import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceReader;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.DatasetSuccessfulQueryAudit;
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

  /**
   * Reconcile the exact immutable DatasetVersion before source LIMIT, rather
   * than allowing later DatasetVersion successes to evict historic evidence.
   */
  public List<UsageNormalizationResult> synchronizeRecentByProductAndVersion(
      long datasetId, long datasetVersionId, int limit) {
    Long projectId = currentProject.requireProjectId();
    return performanceReader.recentSuccessfulByDatasetAndVersion(
            datasetId, datasetVersionId, Math.max(1, Math.min(200, limit))).stream()
        .map(trace -> normalizer.normalize(projectId, trace))
        .toList();
  }

  /**
   * Each request processes at most 200 persisted rows. The continuation token
   * is the smallest database audit ID actually visited, never a display version.
   * A caller must not advance when any normalization has a GAP/UNAVAILABLE.
   */
  public DatasetRecoveryPage recoverSuccessfulVersionPage(
      long datasetId, long datasetVersionId, Long beforeAuditId, int requestedLimit) {
    Long projectId = currentProject.requireProjectId();
    int limit = Math.max(1, Math.min(200, requestedLimit));
    List<DatasetSuccessfulQueryAudit> audits =
        performanceReader.successfulPageByDatasetAndVersion(
            datasetId, datasetVersionId, beforeAuditId, limit);
    // A corrupt or unordered persisted page must not advance the durable recovery cursor.
    Long previousId = beforeAuditId;
    if (audits.size() > limit) {
      throw new IllegalStateException("Persisted Dataset audit page exceeded the requested limit");
    }
    for (DatasetSuccessfulQueryAudit audit : audits) {
      if (audit == null || audit.auditId() <= 0L || (previousId != null && audit.auditId() >= previousId)) {
        throw new IllegalStateException("Persisted Dataset audit page has an invalid descending cursor");
      }
      previousId = audit.auditId();
    }
    List<UsageNormalizationResult> results = audits.stream()
        .map(audit -> normalizer.normalize(projectId, audit.trace()))
        .toList();
    Long next = audits.size() == limit ? audits.getLast().auditId() : null;
    return new DatasetRecoveryPage(results, next, audits.size() < limit);
  }

  public record DatasetRecoveryPage(
      List<UsageNormalizationResult> results, Long nextBeforeAuditId, boolean exhausted) {
    public DatasetRecoveryPage {
      results = List.copyOf(results);
    }
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
