package io.yak.ops.business.modeling.standard;

import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.ApprovalFreeze;
import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.DesignBinding;
import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.Result;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Evaluation;
import io.yak.ops.common.version.VersionDigests;
import java.util.Objects;

/**
 * Review-only proposed B2 revalidation protocol for detecting change-and-revert (ABA).
 *
 * <p>Not a Spring bean and NOT a publish/authorization gate. Epochs supplied to this
 * pure function are UNTRUSTED until the Semantic and Security owners define and
 * implement authoritative, monotonic, project-scoped revision sources. A compare
 * result cannot guarantee atomicity across a Semantic read and Modeling commit.
 */
public final class DraftPublicationEpochGuard {
  public static final String PROPOSED_SCHEME = "B2_MONOTONIC_EPOCH_PROPOSAL_V1";

  private DraftPublicationEpochGuard() {}

  /**
   * A hypothetical jointly scoped observation. Epochs MUST advance on every
   * relevant semantic mutation and authorization revocation/grant, including
   * mutations which later restore the original value. A free-form token,
   * digest, wall clock or per-request counter cannot substitute for this.
   */
  public record EpochObservation(
      String scheme, long projectId, long modelId, String layerCode,
      String physicalStructureSha256, long semanticEpoch, long authorizationEpoch) {}

  public record GuardedFreeze(
      ApprovalFreeze approval, EpochObservation epochs, String guardDigestSha256) {}

  public enum Verdict { CANDIDATE_MATCH, STALE, UNKNOWN }

  public record Capture(Verdict verdict, String reason, GuardedFreeze freeze) {
    static Capture unknown(String reason) {
      return new Capture(Verdict.UNKNOWN, reason, null);
    }
  }

  public record Comparison(Verdict verdict, String reason) {}

  public static Capture capture(ApprovalFreeze approval, EpochObservation epochs) {
    if (!validApproval(approval) || !validEpoch(epochs, approval.binding())) {
      return Capture.unknown("审批证据或语义/授权单调纪元不能复核");
    }
    return new Capture(Verdict.CANDIDATE_MATCH,
        "仅为双纪元候选冻结；未提供可信的纪元来源或发布权限",
        new GuardedFreeze(approval, epochs, guardDigest(approval, epochs)));
  }

  /**
   * Compare two separately observed epoch states, plus the current design and
   * standard candidate. Even matching epochs require a trusted final read and
   * the owner-approved publish-time atomicity/revalidation protocol.
   */
  public static Comparison compare(
      GuardedFreeze frozen, DesignBinding currentDesign,
      Evaluation currentStandards, EpochObservation currentEpochs) {
    if (frozen == null || !validApproval(frozen.approval())
        || !validEpoch(frozen.epochs(), frozen.approval().binding())
        || !Objects.equals(frozen.guardDigestSha256(),
            guardDigest(frozen.approval(), frozen.epochs()))) {
      return new Comparison(Verdict.UNKNOWN, "双纪元冻结损坏、缺失或未被验证");
    }
    Result candidate = DraftPublicationApprovalEvidence.compare(
        frozen.approval(), currentDesign, currentStandards).result();
    if (candidate == Result.UNKNOWN) {
      return new Comparison(Verdict.UNKNOWN, "当前设计或标准证据不足");
    }
    if (candidate == Result.STALE) {
      return new Comparison(Verdict.STALE, "设计、映射或标准证据偏离原审批");
    }
    if (!validEpoch(currentEpochs, currentDesign)) {
      return new Comparison(Verdict.UNKNOWN, "当前纪元身份或来源语义不可验证");
    }
    // A rollback of a monotonic epoch signals stale/replayed observations.
    if (currentEpochs.semanticEpoch() < frozen.epochs().semanticEpoch()
        || currentEpochs.authorizationEpoch() < frozen.epochs().authorizationEpoch()) {
      return new Comparison(Verdict.UNKNOWN, "观察纪元倒退：可能为供应方旧读或重放");
    }
    // Detect changes even if the original value and its content digest returned.
    if (currentEpochs.semanticEpoch() > frozen.epochs().semanticEpoch()
        || currentEpochs.authorizationEpoch() > frozen.epochs().authorizationEpoch()) {
      return new Comparison(Verdict.STALE, "标准事实或权限曾改变，旧审批必须重新送审");
    }
    return new Comparison(Verdict.CANDIDATE_MATCH,
        "双纪元候选一致，但没有跨域提交原子性、实际授权或发布资格");
  }

  private static boolean validApproval(ApprovalFreeze approval) {
    if (approval == null || approval.binding() == null || approval.standards() == null) {
      return false;
    }
    Evaluation frozenEvaluation = new Evaluation(DraftPublicationEvidenceVerifier.Verdict.VALID_CANDIDATE,
        "isolated frozen evidence", approval.standards());
    return DraftPublicationApprovalEvidence.compare(
        approval, approval.binding(), frozenEvaluation).result() == Result.CANDIDATE_MATCH;
  }

  private static boolean validEpoch(EpochObservation epoch, DesignBinding binding) {
    return epoch != null && binding != null
        && PROPOSED_SCHEME.equals(epoch.scheme())
        && epoch.projectId() == binding.projectId()
        && epoch.modelId() == binding.modelId()
        && Objects.equals(epoch.layerCode(), binding.layerCode())
        && Objects.equals(epoch.physicalStructureSha256(),
            binding.physicalStructureSha256())
        && epoch.semanticEpoch() > 0 && epoch.authorizationEpoch() > 0;
  }

  private static String guardDigest(ApprovalFreeze approval, EpochObservation epoch) {
    StringBuilder canonical = new StringBuilder();
    append(canonical, PROPOSED_SCHEME);
    append(canonical, approval.freezeAlgorithm());
    append(canonical, approval.approvalEvidenceSha256());
    append(canonical, epoch.scheme());
    append(canonical, Long.toString(epoch.projectId()));
    append(canonical, Long.toString(epoch.modelId()));
    append(canonical, epoch.layerCode());
    append(canonical, epoch.physicalStructureSha256());
    append(canonical, Long.toString(epoch.semanticEpoch()));
    append(canonical, Long.toString(epoch.authorizationEpoch()));
    return VersionDigests.sha256Hex(canonical.toString());
  }

  private static void append(StringBuilder target, String value) {
    if (value == null) target.append("-1:");
    else target.append(value.length()).append(':').append(value);
  }
}
