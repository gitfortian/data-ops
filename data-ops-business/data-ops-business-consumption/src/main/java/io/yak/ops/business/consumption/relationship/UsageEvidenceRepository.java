package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import java.util.List;
import java.util.Optional;

public interface UsageEvidenceRepository {
  Optional<UsageEvidence> findByDeduplicationId(Long projectId, String deduplicationId);
  UsageEvidence save(UsageEvidence evidence);
  List<UsageEvidence> list(Long projectId, ProductKey productKey, ConsumerRef consumerRef, int limit);

  /** Persisted successful Usage for exactly one immutable source version, still Project-scoped. */
  List<UsageEvidence> listByVersion(Long projectId, ProductKey productKey, String sourceVersionIdentity, int limit);
}
