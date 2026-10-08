package io.yak.ops.business.consumption.relationship;

/**
 * One bounded persisted Dataset success-audit recovery page.
 * End-of-audit only refers to the retained source audit, not all historical usage.
 */
public record DatasetAuditRecoveryView(
    String productKey,
    String sourceVersionIdentity,
    Long requestedBeforeAuditId,
    int requestedLimit,
    int visitedAuditCount,
    int normalizedOrAlreadyPresentCount,
    int normalizationGapCount,
    int normalizationUnavailableCount,
    Long nextBeforeAuditId,
    boolean retryRequired,
    boolean retainedAuditExhausted) {
}
