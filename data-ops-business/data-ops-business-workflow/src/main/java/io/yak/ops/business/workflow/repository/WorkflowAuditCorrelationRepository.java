package io.yak.ops.business.workflow.repository;

import java.util.Optional;

/** Narrow persistence port for durable WorkflowExecution -> AuditCarrier correlation. */
public interface WorkflowAuditCorrelationRepository {

  Optional<String> findCarrierJson(String executionId);

  /** Replaces the frozen carrier. A null value clears correlation after a failed reactivation. */
  boolean replaceCarrierJson(String executionId, String carrierJson);
}
