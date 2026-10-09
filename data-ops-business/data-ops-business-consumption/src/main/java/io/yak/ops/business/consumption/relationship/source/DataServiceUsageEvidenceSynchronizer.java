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

  /**
   * Single bounded page of source-owned successful invocation audit.
   * The last persisted invocation ID is the exclusive next-page cursor.
   */
  public DataServiceRecoveryPage recoverSuccessfulRevisionPage(
      Long apiId, Long sourceRevisionId, Long beforeInvocationId, int requestedLimit) {
    int limit = Math.max(1, Math.min(200, requestedLimit));
    List<InvocationRecord> audit = callLogReader.successfulPageByApiAndRevision(
        apiId, sourceRevisionId, beforeInvocationId, limit);
    // Reject corrupt, duplicated or unsorted pages before any usage normalization writes.
    Long previousId = beforeInvocationId;
    if (audit.size() > limit) {
      throw new IllegalStateException("Persisted invocation recovery page exceeded the requested limit");
    }
    for (InvocationRecord invocation : audit) {
      if (invocation == null || invocation.id() == null || invocation.id() <= 0L
          || (previousId != null && invocation.id() >= previousId)) {
        throw new IllegalStateException("Persisted invocation recovery page has an invalid descending cursor");
      }
      previousId = invocation.id();
    }
    List<UsageNormalizationResult> normalized = audit.stream()
        .map(normalizer::normalize)
        .toList();
    Long next = audit.size() == limit ? audit.getLast().id() : null;
    return new DataServiceRecoveryPage(normalized, next, audit.size() < limit);
  }

  public record DataServiceRecoveryPage(
      List<UsageNormalizationResult> results, Long nextBeforeInvocationId, boolean exhausted) {
    public DataServiceRecoveryPage {
      results = List.copyOf(results);
    }
  }

  public List<UsageNormalizationResult> synchronizeRecentByProduct(Long apiId, int limit) {
    return callLogReader.recentSuccessfulByApi(apiId, Math.max(1, Math.min(200, limit))).stream()
        .map(normalizer::normalize)
        .toList();
  }
}
