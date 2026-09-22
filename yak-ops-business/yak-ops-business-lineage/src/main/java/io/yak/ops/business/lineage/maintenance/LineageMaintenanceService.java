package io.yak.ops.business.lineage.maintenance;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable maintenance facade over replacement and revision roles. */
@Service
public class LineageMaintenanceService {

  private final LineageEvidenceReplacementCoordinator replacementCoordinator;
  private final LineageRevisionGuard revisionGuard;

  public LineageMaintenanceService(
      LineageEvidenceReplacementCoordinator replacementCoordinator,
      LineageRevisionGuard revisionGuard) {
    this.replacementCoordinator = replacementCoordinator;
    this.revisionGuard = revisionGuard;
  }

  @Transactional("yakBusinessTransactionManager")
  public int clearRelationsByEvidence(String sourceType, String sourceId) {
    return replacementCoordinator.clearRelationsByEvidence(sourceType, sourceId);
  }

  @Transactional("yakBusinessTransactionManager")
  public CleanupScope beginReplacement(
      String sourceType, String sourceId, String ownerType, String ownerId) {
    return replacementCoordinator.beginReplacement(
        sourceType, sourceId, ownerType, ownerId);
  }

  @Transactional("yakBusinessTransactionManager")
  public int finishReplacement(CleanupScope scope) {
    return replacementCoordinator.finishReplacement(scope);
  }

  @Transactional("yakBusinessTransactionManager")
  public boolean lockAndAcceptRevision(String assetKey, int revisionNo) {
    return revisionGuard.lockAndAcceptRevision(assetKey, revisionNo);
  }

  public record CleanupScope(
      String sourceType,
      String sourceId,
      String ownerType,
      String ownerId,
      Set<Long> candidateAssetIds) {

    public CleanupScope {
      candidateAssetIds =
          candidateAssetIds == null ? Set.of() : Set.copyOf(candidateAssetIds);
    }
  }
}
