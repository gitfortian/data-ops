package io.yak.ops.business.development.handoff;

/**
 * Durable DataDev attempt identity. Runtime success is NOT target-data reconciliation.
 * SQL definitions and raw Runtime output are intentionally excluded from the receipt.
 */
public record DwdFrozenExecutionAttempt(
    Long id, long projectId, long bindingId, String bindingDigest,
    String requestKey, String attemptDigest, String state,
    String runtimeExecutionId, String runtimeStatus) {}
