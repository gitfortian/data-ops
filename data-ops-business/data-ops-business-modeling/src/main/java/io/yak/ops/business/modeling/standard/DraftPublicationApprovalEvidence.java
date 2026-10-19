package io.yak.ops.business.modeling.standard;

import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Evaluation;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.FrozenCandidate;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Verdict;
import io.yak.ops.common.version.VersionDigests;
import java.util.Objects;

/**
 * Review-only B2 approval evidence binding / recheck prototype.
 *
 * <p>Not a Spring bean, controller, publisher, authorizer or valid approval
 * token. Its output only establishes deterministic candidate equality.
 * A Semantic owner-approved time/concurrency consistency contract and
 * Modeling/Security signoffs are needed before an actual publish transaction
 * may use this strategy. This cannot prevent change-and-revert (ABA).
 */
public final class DraftPublicationApprovalEvidence {
  private static final String PROPOSAL = "MODEL_APPROVAL_CANDIDATE_V1";

  private DraftPublicationApprovalEvidence() {}

  /**
   * Domain-owned exact design references. Mapped revisions are either fully
   * specified (revision ID + content SHA + logical SHA) or all absent.
   */
  public record DesignBinding(
      long projectId, long modelId, String layerCode, String physicalStructureSha256,
      Long mappingSetId, Long mappingRevisionId, String mappingContentSha256,
      Long logicalModelId, Integer logicalVersionNo, String logicalSnapshotSha256) {}

  /** Captured proposed evidence; the snapshot contains only hashed standards. */
  public record ApprovalFreeze(DesignBinding binding, FrozenCandidate standards,
                               String freezeAlgorithm, String approvalEvidenceSha256) {}

  public enum Result { CANDIDATE_MATCH, STALE, UNKNOWN }

  public record FreezeResult(Result result, String reason, ApprovalFreeze freeze) {
    static FreezeResult reject(String reason) {
      return new FreezeResult(Result.UNKNOWN, reason, null);
    }
  }

  public record Comparison(Result result, String reason) {
    public boolean isMatchingCandidate() {
      return result == Result.CANDIDATE_MATCH;
    }
  }

  /** Produce a detached evidence candidate; this never stores an approval. */
  public static FreezeResult freeze(DesignBinding binding, Evaluation evaluation) {
    if (!validBinding(binding)) {
      return FreezeResult.reject("审批设计身份或映射修订不完整");
    }
    FrozenCandidate candidate = validCandidate(binding, evaluation);
    if (candidate == null) {
      return FreezeResult.reject("当前标准证据未知、缺失或并非当前设计");
    }
    String digest = frozenDigest(binding, candidate);
    return new FreezeResult(Result.CANDIDATE_MATCH,
        "仅供隔离审查；不代表审批通过、发布资格或跨域原子性",
        new ApprovalFreeze(binding, candidate, PROPOSAL, digest));
  }

  /**
   * Must be invoked again against a separately revalidated and authorized
   * Semantic observation. Equality of hashes/token does NOT make that read
   * atomic with the Modeling publication transaction.
   */
  public static Comparison compare(
      ApprovalFreeze frozen, DesignBinding current, Evaluation currentEvaluation) {
    if (frozen == null || !validBinding(frozen.binding())
        || !validHash(frozen.approvalEvidenceSha256())
        || !PROPOSAL.equals(frozen.freezeAlgorithm())
        || validCandidate(frozen.binding(), new Evaluation(
            Verdict.VALID_CANDIDATE, "frozen proposal", frozen.standards())) == null) {
      return new Comparison(Result.UNKNOWN, "审批冻结证据未建立或已损坏");
    }
    if (!Objects.equals(frozenDigest(frozen.binding(), frozen.standards()),
        frozen.approvalEvidenceSha256())) {
      return new Comparison(Result.UNKNOWN, "审批冻结内容与摘要不一致");
    }
    if (!validBinding(current)) {
      return new Comparison(Result.UNKNOWN, "发布时的当前设计身份无法核验");
    }
    if (!frozen.binding().equals(current)) {
      return new Comparison(Result.STALE, "逻辑/物理/映射修订已偏离审批绑定");
    }
    FrozenCandidate latest = validCandidate(current, currentEvaluation);
    if (latest == null) {
      return new Comparison(Result.UNKNOWN, "发布时的标准状态不能复核");
    }
    if (!Objects.equals(frozenDigest(current, latest), frozen.approvalEvidenceSha256())) {
      return new Comparison(Result.STALE, "发布时的标准摘要或 Semantic 观察标识已改变");
    }
    return new Comparison(Result.CANDIDATE_MATCH,
        "候选证据一致，但不能单独授权发布或保证跨事务原子性");
  }

  private static FrozenCandidate validCandidate(DesignBinding binding, Evaluation evaluation) {
    if (evaluation == null || evaluation.verdict() != Verdict.VALID_CANDIDATE
        || evaluation.snapshot() == null) {
      return null;
    }
    FrozenCandidate candidate = evaluation.snapshot();
    if (candidate.projectId() != binding.projectId()
        || candidate.modelId() != binding.modelId()
        || !Objects.equals(candidate.layerCode(), binding.layerCode())
        || !Objects.equals(candidate.structureSha256(), binding.physicalStructureSha256())
        || !validHash(candidate.candidateDigestSha256())
        || !DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM.equals(
            candidate.semanticDigestAlgorithm())
        || !validHash(candidate.semanticEvidenceSha256())
        || !DraftPublicationEvidenceVerifier.internallyConsistent(candidate)) {
      return null;
    }
    return candidate;
  }

  private static boolean validBinding(DesignBinding binding) {
    if (binding == null || binding.projectId() <= 0 || binding.modelId() <= 0
        || !validHash(binding.physicalStructureSha256())
        || !java.util.Set.of("DWD", "DIM", "DWS", "ADS").contains(
            binding.layerCode() == null ? "" : binding.layerCode())) {
      return false;
    }
    if (binding.mappingRevisionId() == null) {
      return binding.mappingSetId() == null && binding.mappingContentSha256() == null
          && binding.logicalModelId() == null && binding.logicalVersionNo() == null
          && binding.logicalSnapshotSha256() == null;
    }
    return binding.mappingSetId() != null && binding.mappingSetId() > 0
        && binding.mappingRevisionId() > 0
        && binding.logicalModelId() != null && binding.logicalModelId() > 0
        && binding.logicalVersionNo() != null && binding.logicalVersionNo() > 0
        && validHash(binding.mappingContentSha256())
        && validHash(binding.logicalSnapshotSha256());
  }

  private static String frozenDigest(DesignBinding binding, FrozenCandidate candidate) {
    StringBuilder stable = new StringBuilder();
    append(stable, PROPOSAL);
    append(stable, Long.toString(binding.projectId()));
    append(stable, Long.toString(binding.modelId()));
    append(stable, binding.layerCode());
    append(stable, binding.physicalStructureSha256());
    append(stable, Objects.toString(binding.mappingSetId(), null));
    append(stable, Objects.toString(binding.mappingRevisionId(), null));
    append(stable, binding.mappingContentSha256());
    append(stable, Objects.toString(binding.logicalModelId(), null));
    append(stable, Objects.toString(binding.logicalVersionNo(), null));
    append(stable, binding.logicalSnapshotSha256());
    append(stable, candidate.semanticDigestAlgorithm());
    append(stable, candidate.semanticEvidenceSha256());
    append(stable, candidate.candidateDigestSha256());
    return VersionDigests.sha256Hex(stable.toString());
  }

  private static void append(StringBuilder target, String value) {
    if (value == null) target.append("-1:");
    else target.append(value.length()).append(':').append(value);
  }

  private static boolean validHash(String value) {
    return value != null && value.matches("[a-f0-9]{64}");
  }
}
