package io.yak.ops.business.security.support.audit;

import io.yak.ops.business.audit.*;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Thin wrapper that opens a business-audit operation, runs the business body, and lands the
 * audit event after commit (or immediately when non-transactional). Failures are recorded and
 * rethrown. Audit remains fail-open and never a business dependency.
 */
public final class SecurityAudit {

  private SecurityAudit() {}

  public static <T> T tx(
      BusinessAuditService audit,
      AuditEventType eventType,
      AuditOperationRequest request,
      Supplier<T> body) {
    AuditOperationHandle handle = audit.start(request);
    try {
      T result = body.get();
      AuditTransactions.completeOnCommit(
          handle, eventType, request.operationName(), Map.of(), request.operationName());
      return result;
    } catch (RuntimeException exception) {
      handle.failure(request.operationType() + "_FAILED", exception);
      throw exception;
    }
  }

  public static AuditOperationRequest request(
      String opType, String name, String resourceType, String resourceId, String resourceName) {
    return new AuditOperationRequest(
        opType, name, resourceType, resourceId, resourceName, "APPLICATION", Map.of());
  }
}
