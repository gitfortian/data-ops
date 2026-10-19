package io.yak.ops.business.modeling.standard;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.ApprovalFreeze;
import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.DesignBinding;
import io.yak.ops.business.modeling.standard.DraftPublicationEpochGuard.EpochObservation;
import io.yak.ops.business.modeling.standard.DraftPublicationEpochGuard.GuardedFreeze;
import io.yak.ops.business.modeling.standard.DraftPublicationEpochGuard.Verdict;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnFact;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnRequest;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Evaluation;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Observation;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Request;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Review-only two-domain epoch proposal. This does not test an actual Semantic
 * provider, authorization service, approval persistence or publish transaction.
 */
class DraftPublicationEpochGuardTest {
  private static final String STRUCTURE = "a".repeat(64);
  private static final String SEMANTIC = "b".repeat(64);
  private static final String STANDARD = "c".repeat(64);

  private static DesignBinding binding() {
    return new DesignBinding(7L, 701L, "DWD", STRUCTURE,
        501L, 901L, "d".repeat(64), 77L, 2, "e".repeat(64));
  }

  private static Evaluation standards(String standardDigest) {
    var columns = List.of(
        new ColumnRequest("order_id", "BIGINT", 11L),
        new ColumnRequest("order_status", "VARCHAR", 12L));
    var facts = List.of(
        new ColumnFact("order_id", "BIGINT", 11L, "VALID", standardDigest,
            "NOT_APPLICABLE", null, null, null),
        new ColumnFact("order_status", "VARCHAR", 12L, "VALID", standardDigest,
            "ENABLED", 3, 3, "f".repeat(64)));
    return DraftPublicationEvidenceVerifier.evaluate(
        new Request(7L, 701L, "DWD", STRUCTURE, columns),
        new Observation(7L, 701L, "DWD", STRUCTURE, "VALID",
            DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM,
            SEMANTIC, "provider-opaque-token", facts));
  }

  private static ApprovalFreeze approved() {
    return DraftPublicationApprovalEvidence.freeze(binding(), standards(STANDARD)).freeze();
  }

  private static EpochObservation epoch(long semantic, long authorization) {
    return new EpochObservation(DraftPublicationEpochGuard.PROPOSED_SCHEME,
        7L, 701L, "DWD", STRUCTURE, "9".repeat(64), semantic, authorization);
  }

  private static GuardedFreeze capture() {
    return DraftPublicationEpochGuard.capture(approved(), epoch(40, 90)).freeze();
  }

  @Test
  void stableBothEpochsIsOnlyCandidateMatchAndDoesNotAuthorizePublish() {
    var result = DraftPublicationEpochGuard.compare(
        capture(), binding(), standards(STANDARD), epoch(40, 90));
    assertThat(result.verdict()).isEqualTo(Verdict.CANDIDATE_MATCH);
    assertThat(result.reason()).contains("没有跨域提交原子性");
  }

  @Test
  void semanticChangeAndRevertWithSameEvidenceDigestStillInvalidatesApproval() {
    // The hypothetical Semantic owner must advance epoch for both modifications.
    var sameFactsAfterAba = standards(STANDARD);
    assertThat(DraftPublicationEpochGuard.compare(
        capture(), binding(), sameFactsAfterAba, epoch(42, 90)).verdict())
        .isEqualTo(Verdict.STALE);
  }

  @Test
  void permissionRevocationAndRegrantWithSameAuthorizationResultIsStale() {
    // Authorization epoch changes even if the user later regains permissions.
    assertThat(DraftPublicationEpochGuard.compare(
        capture(), binding(), standards(STANDARD), epoch(40, 92)).verdict())
        .isEqualTo(Verdict.STALE);
  }

  @Test
  void replayedOrRolledBackEpochMustBeUnknownNotAnApprovedMatch() {
    for (EpochObservation old : List.of(epoch(39, 90), epoch(40, 89), epoch(39, 89))) {
      assertThat(DraftPublicationEpochGuard.compare(
          capture(), binding(), standards(STANDARD), old).verdict())
          .isEqualTo(Verdict.UNKNOWN);
    }
  }

  @Test
  void providerUnknownZeroRevisionsAndMismatchedScopeAreRejected() {
    var approval = approved();
    assertThat(DraftPublicationEpochGuard.capture(approval, epoch(0, 90)).verdict())
        .isEqualTo(Verdict.UNKNOWN);
    assertThat(DraftPublicationEpochGuard.capture(approval, epoch(40, 0)).freeze())
        .isNull();

    var otherProject = new EpochObservation(DraftPublicationEpochGuard.PROPOSED_SCHEME,
        8L, 701L, "DWD", STRUCTURE, "9".repeat(64), 40, 90);
    var otherModel = new EpochObservation(DraftPublicationEpochGuard.PROPOSED_SCHEME,
        7L, 702L, "DWD", STRUCTURE, "9".repeat(64), 40, 90);
    var wrongScheme = new EpochObservation("LEGACY_UNVERSIONED",
        7L, 701L, "DWD", STRUCTURE, "9".repeat(64), 40, 90);
    for (EpochObservation invalid : List.of(otherProject, otherModel, wrongScheme)) {
      assertThat(DraftPublicationEpochGuard.capture(approval, invalid).verdict())
          .isEqualTo(Verdict.UNKNOWN);
      assertThat(DraftPublicationEpochGuard.compare(
          capture(), binding(), standards(STANDARD), invalid).verdict())
          .isEqualTo(Verdict.UNKNOWN);
    }
    assertThat(DraftPublicationEpochGuard.compare(
        capture(), binding(), standards(STANDARD), null).verdict())
        .isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void changingStandardsOrDesignFailsEvenWithStableEpochs() {
    var changedFacts = standards("1".repeat(64));
    assertThat(DraftPublicationEpochGuard.compare(
        capture(), binding(), changedFacts, epoch(40, 90)).verdict())
        .isEqualTo(Verdict.STALE);
    var changedDesign = new DesignBinding(7L, 701L, "DWD", STRUCTURE,
        501L, 902L, "d".repeat(64), 77L, 2, "e".repeat(64));
    assertThat(DraftPublicationEpochGuard.compare(
        capture(), changedDesign, standards(STANDARD), epoch(40, 90)).verdict())
        .isEqualTo(Verdict.STALE);
  }

  @Test
  void approvalCannotBeReusedByAnotherAuthorizationSubjectAtSameEpoch() {
    var differentUserOrPermissions = new EpochObservation(
        DraftPublicationEpochGuard.PROPOSED_SCHEME,
        7L, 701L, "DWD", STRUCTURE, "8".repeat(64), 40, 90);
    assertThat(DraftPublicationEpochGuard.compare(
        capture(), binding(), standards(STANDARD), differentUserOrPermissions).verdict())
        .isEqualTo(Verdict.STALE);
    var malformedScope = new EpochObservation(
        DraftPublicationEpochGuard.PROPOSED_SCHEME,
        7L, 701L, "DWD", STRUCTURE, "untrusted", 40, 90);
    assertThat(DraftPublicationEpochGuard.capture(approved(), malformedScope).verdict())
        .isEqualTo(Verdict.UNKNOWN);
    assertThat(DraftPublicationEpochGuard.compare(
        capture(), binding(), standards(STANDARD), malformedScope).verdict())
        .isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void tamperedOrIncompleteFrozenEpochsCannotBeReused() {
    var frozen = capture();
    var replacedEpoch = new GuardedFreeze(
        frozen.approval(), epoch(40, 100), frozen.guardDigestSha256());
    var forgedHash = new GuardedFreeze(
        frozen.approval(), frozen.epochs(), "0".repeat(64));
    for (GuardedFreeze forged : List.of(replacedEpoch, forgedHash)) {
      assertThat(DraftPublicationEpochGuard.compare(
          forged, binding(), standards(STANDARD), epoch(40, 90)).verdict())
          .isEqualTo(Verdict.UNKNOWN);
    }
    assertThat(DraftPublicationEpochGuard.compare(
        null, binding(), standards(STANDARD), epoch(40, 90)).verdict())
        .isEqualTo(Verdict.UNKNOWN);
  }
}
