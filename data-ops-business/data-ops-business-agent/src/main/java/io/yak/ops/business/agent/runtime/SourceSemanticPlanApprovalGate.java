package io.yak.ops.business.agent.runtime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Explicit application approval boundary. Must be called only after the authenticated user
 * reviewed the exact plan hash and the server independently revalidated source access.
 * This adapter cannot itself grant access to the datasource or execute an Agent turn.
 */
public final class SourceSemanticPlanApprovalGate {
  private final SourceSemanticTaskLedger ledger;
  private final SourceSemanticPlanDocumentGuard files;

  public SourceSemanticPlanApprovalGate(SourceSemanticTaskLedger ledger,
      SourceSemanticPlanDocumentGuard files) {
    this.ledger = Objects.requireNonNull(ledger);
    this.files = Objects.requireNonNull(files);
  }

  public SourceSemanticTaskState approve(String ownerId, long projectId, String taskId,
      String verifiedScopeFingerprint, Path trustedTaskWorkspace, String reviewedPlanSha) {
    SourceSemanticTaskState before = ledger.read(ownerId, projectId, taskId);
    if (!Objects.equals(before.scopeFingerprint(), verifiedScopeFingerprint))
      throw new IllegalStateException("[F039_SOURCE_OR_PLAN_DRIFT]");
    files.verifyApproved(trustedTaskWorkspace, reviewedPlanSha);
    return ledger.confirmPlan(ownerId, projectId, taskId, verifiedScopeFingerprint,
        reviewedPlanSha);
  }

  public SourceSemanticTaskState resumeAfterRecheck(String ownerId, long projectId, String taskId,
      String verifiedScopeFingerprint, Path trustedTaskWorkspace, String reviewedPlanSha) {
    SourceSemanticTaskState before = ledger.read(ownerId, projectId, taskId);
    if (!Objects.equals(before.scopeFingerprint(), verifiedScopeFingerprint))
      throw new IllegalStateException("[F039_SOURCE_OR_PLAN_DRIFT]");
    files.verifyApproved(trustedTaskWorkspace, reviewedPlanSha);
    return ledger.resume(ownerId, projectId, taskId, verifiedScopeFingerprint,
        reviewedPlanSha);
  }
}
