package io.yak.ops.business.consumption.relationship.source;

import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.dataservice.observability.DataServiceCallLogReader;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Retryable projection from persistent source invocation audit into Consumption Usage Evidence. */
@Service
@RequiredArgsConstructor
public class DataServiceUsageEvidenceSynchronizer {

  private final DataServiceCallLogReader callLogReader;
  private final DataServiceUsageEvidenceNormalizer normalizer;

  public List<UsageNormalizationResult> synchronizeRecent(int limit) {
    int boundedLimit = Math.max(1, Math.min(200, limit));
    return callLogReader.recent().stream()
        .limit(boundedLimit)
        .map(normalizer::normalize)
        .toList();
  }

  /**
   * A historic immutable revision may still have source-owned successful
   * invocation audits that were never normalized. Reconcile those records
   * independently of the newest successful calls of other revisions.
   */
  public List<UsageNormalizationResult> synchronizeRecentByProductAndRevision(
      Long apiId, Long sourceRevisionId, int limit) {
    return callLogReader.recentSuccessfulByApiAndRevision(
            apiId, sourceRevisionId, Math.max(1, Math.min(200, limit))).stream()
        .map(normalizer::normalize)
        .toList();
  }

  public List<UsageNormalizationResult> synchronizeRecentByProduct(Long apiId, int limit) {
    return callLogReader.recentSuccessfulByApi(apiId, Math.max(1, Math.min(200, limit))).stream()
        .map(normalizer::normalize)
        .toList();
  }
}
