package io.yak.ops.business.sync.offline.repository;

import java.util.Optional;

/** Narrow persistence port for durable Batch -> AuditCarrier correlation. */
public interface OfflineAuditCorrelationRepository {

  Optional<String> findCarrierJson(long batchId);

  boolean updateCarrierJson(long batchId, String carrierJson);
}
