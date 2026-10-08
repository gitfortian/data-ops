package io.yak.ops.business.consumption.relationship;

/**
 * Bounded recovery of successful source-owned Data Service invocation audit.
 * Exhaustion only covers rows still retained by the source audit repository.
 */
public record DataServiceAuditRecoveryView(
    String productKey,
    String sourceVersionIdentity,
    String requestedBeforeInvocationId,
    int requestedLimit,
    int visitedAuditCount,
    int normalizedOrAlreadyPresentCount,
    int normalizationGapCount,
    int normalizationUnavailableCount,
    String nextBeforeInvocationId,
    boolean retryRequired,
    boolean retainedAuditExhausted) {
}
