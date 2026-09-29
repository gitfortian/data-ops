package io.yak.ops.business.audit;

/** Entry point shared by business modules. Audit persistence must never become a business dependency. */
public interface BusinessAuditService {
  AuditOperationHandle start(AuditOperationRequest request);

  default AuditOperationHandle resume(AuditCarrier carrier) {
    return AuditOperationHandle.noop(carrier);
  }

  /** Records or defers one authorization decision without making audit a business dependency. */
  default void authorizationDecision(AuditAuthorizationDecision decision) {}

  /** Clears request-scoped authorization state defensively at request boundaries. */
  default void clearAuthorizationDecisions() {}
}
