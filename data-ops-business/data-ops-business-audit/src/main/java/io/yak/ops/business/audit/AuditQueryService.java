package io.yak.ops.business.audit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-side contract consumed by Audit Center and business attribution views. */
public interface AuditQueryService {

  AuditPage<AuditOperationSummary> page(AuditOperationQuery query);

  Optional<AuditOperationDetail> detail(String operationId);

  AuditFilterOptions options();

  /**
   * Returns the actor name from the first matching audit operation for each supplied resource.
   * Later retry/recovery operations must not replace the original creator attribution.
   */
  default Map<String, String> firstActorNames(
      String operationType,
      String resourceType,
      List<String> resourceIds,
      Long projectId) {
    return Map.of();
  }
}
