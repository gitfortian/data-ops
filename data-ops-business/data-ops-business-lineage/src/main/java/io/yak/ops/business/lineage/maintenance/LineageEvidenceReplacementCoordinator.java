package io.yak.ops.business.lineage.maintenance;

import io.yak.ops.business.lineage.maintenance.LineageMaintenanceService.CleanupScope;
import io.yak.ops.business.lineage.repository.LineageRepository;
import org.springframework.stereotype.Component;

/** Coordinates evidence-scoped relation replacement and conservative asset cleanup. */
@Component
public class LineageEvidenceReplacementCoordinator {

  private final LineageRepository repository;

  public LineageEvidenceReplacementCoordinator(LineageRepository repository) {
    this.repository = repository;
  }

  public int clearRelationsByEvidence(String sourceType, String sourceId) {
    String normalizedType = required(sourceType, "sourceType", 64);
    String normalizedId = required(sourceId, "sourceId", 200);
    return repository.deleteRelationsByEvidence(normalizedType, normalizedId);
  }

  public CleanupScope beginReplacement(
      String sourceType, String sourceId, String ownerType, String ownerId) {
    String evidenceType = required(sourceType, "sourceType", 64);
    String evidenceId = required(sourceId, "sourceId", 200);
    CleanupScope scope =
        new CleanupScope(
            evidenceType,
            evidenceId,
            required(ownerType, "ownerType", 64),
            required(ownerId, "ownerId", 200),
            repository.findAssetIdsByEvidence(evidenceType, evidenceId));
    repository.deleteRelationsByEvidence(evidenceType, evidenceId);
    return scope;
  }

  public int finishReplacement(CleanupScope scope) {
    if (scope == null) throw new IllegalArgumentException("cleanupScope 不能为空");
    return repository.deleteUnreferencedOwnedAssets(
        scope.candidateAssetIds(), scope.ownerType(), scope.ownerId());
  }

  private static String required(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
    }
    return normalized;
  }
}
