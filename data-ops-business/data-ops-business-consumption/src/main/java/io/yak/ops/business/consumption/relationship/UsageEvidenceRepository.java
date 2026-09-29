package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import java.util.List;
import java.util.Optional;

public interface UsageEvidenceRepository {
  Optional<UsageEvidence> findByDeduplicationId(Long projectId, String deduplicationId);
  UsageEvidence save(UsageEvidence evidence);
  List<UsageEvidence> list(Long projectId, ProductKey productKey, ConsumerRef consumerRef, int limit);
}
