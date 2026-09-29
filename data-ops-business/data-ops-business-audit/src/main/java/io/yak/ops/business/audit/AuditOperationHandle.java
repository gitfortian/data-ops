package io.yak.ops.business.audit;

import java.util.Map;

/** Handle for appending events and completing one business operation. Implementations are fail-open. */
public interface AuditOperationHandle {

  String operationId();

  default AuditCarrier carrier() {
    return null;
  }

  void resource(String resourceId, String resourceName);

  void event(AuditEventType type, String message, Map<String, ?> payload);

  default void event(AuditEventRequest request) {
    event(request.type(), request.message(), request.payload());
  }

  void success(String summary);

  void failure(String reasonCode, Throwable cause);

  static AuditOperationHandle noop(AuditCarrier carrier) {
    return new AuditOperationHandle() {
      @Override
      public String operationId() {
        return carrier == null ? null : carrier.operationId();
      }

      @Override
      public AuditCarrier carrier() {
        return carrier;
      }

      @Override
      public void resource(String resourceId, String resourceName) {}

      @Override
      public void event(AuditEventType type, String message, Map<String, ?> payload) {}

      @Override
      public void event(AuditEventRequest request) {}

      @Override
      public void success(String summary) {}

      @Override
      public void failure(String reasonCode, Throwable cause) {}
    };
  }
}
