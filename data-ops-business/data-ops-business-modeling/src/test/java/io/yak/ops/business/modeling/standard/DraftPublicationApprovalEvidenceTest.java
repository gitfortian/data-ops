package io.yak.ops.business.modeling.standard;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.ApprovalFreeze;
import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.Comparison;
import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.DesignBinding;
import io.yak.ops.business.modeling.standard.DraftPublicationApprovalEvidence.Result;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnFact;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnRequest;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Evaluation;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Observation;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Request;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Review-only approval recheck candidate tests; no service, DB or publish invocation. */
class DraftPublicationApprovalEvidenceTest {
  private static final String STRUCTURE = "a".repeat(64);
  private static final String SEMANTIC = "b".repeat(64);
  private static final String STANDARD = "c".repeat(64);
  private static final String MAPPING = "d".repeat(64);
  private static final String LOGICAL = "e".repeat(64);

  private static DesignBinding design() {
    return new DesignBinding(7L, 701L, "DWD", STRUCTURE, 901L, MAPPING, LOGICAL);
  }

  private static Evaluation observe(String semanticSha, String token, String standardSha) {
    List<ColumnRequest> columns = List.of(
        new ColumnRequest("order_id", "BIGINT", 11L),
        new ColumnRequest("order_status", "VARCHAR", 12L));
    List<ColumnFact> facts = List.of(
        new ColumnFact("order_id", "BIGINT", 11L, "VALID", standardSha,
            "NOT_APPLICABLE", null, null, null),
        new ColumnFact("order_status", "VARCHAR", 12L, "VALID", standardSha,
            "ENABLED", 3, 3, "f".repeat(64)));
    return DraftPublicationEvidenceVerifier.evaluate(
        new Request(7L, 701L, "DWD", STRUCTURE, columns),
        new Observation(7L, 701L, "DWD", STRUCTURE, "VALID",
            DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM, semanticSha, token, facts));
  }

  private static Evaluation valid() {
    return observe(SEMANTIC, "snapshot#1", STANDARD);
  }

  @Test
  void freezesExactMappingRevisionAndStandardsAsDetachedCandidateNotAnApproval() {
    var evaluated = valid();
    var result = DraftPublicationApprovalEvidence.freeze(design(), evaluated);
    assertThat(result.result()).isEqualTo(Result.CANDIDATE_MATCH);
    ApprovalFreeze freeze = result.freeze();
    assertThat(freeze).isNotNull();
    assertThat(freeze.binding().mappingRevisionId()).isEqualTo(901L);
    assertThat(freeze.binding().mappingContentSha256()).isEqualTo(MAPPING);
    assertThat(freeze.binding().logicalSnapshotSha256()).isEqualTo(LOGICAL);
    assertThat(freeze.standards().columns()).hasSize(2);
    assertThat(freeze.approvalEvidenceSha256()).matches("[a-f0-9]{64}");
    assertThat(result.reason()).contains("不代表审批通过");
  }

  @Test
  void exactRevalidationIsOnlyCandidateMatchAndNotPublishingPermission() {
    var frozen = DraftPublicationApprovalEvidence.freeze(design(), valid()).freeze();
    Comparison result = DraftPublicationApprovalEvidence.compare(frozen, design(), valid());
    assertThat(result.result()).isEqualTo(Result.CANDIDATE_MATCH);
    assertThat(result.reason()).contains("不能单独授权发布");
    assertThat(result.isMatchingCandidate()).isTrue();
  }

  @Test
  void changedMappingRevisionLogicalVersionOrPhysicalStructureIsStale() {
    var frozen = DraftPublicationApprovalEvidence.freeze(design(), valid()).freeze();
    for (DesignBinding changed : List.of(
        new DesignBinding(7L, 701L, "DWD", STRUCTURE, 902L, MAPPING, LOGICAL),
        new DesignBinding(7L, 701L, "DWD", STRUCTURE, 901L, "1".repeat(64), LOGICAL),
        new DesignBinding(7L, 701L, "DWD", STRUCTURE, 901L, MAPPING, "2".repeat(64)),
        new DesignBinding(7L, 701L, "DWD", "3".repeat(64), 901L, MAPPING, LOGICAL))) {
      assertThat(DraftPublicationApprovalEvidence.compare(frozen, changed, valid()).result())
          .isEqualTo(Result.STALE);
    }
  }

  @Test
  void semanticDigestContentOrObservationTokenDriftPreventsApprovalReuse() {
    var frozen = DraftPublicationApprovalEvidence.freeze(design(), valid()).freeze();
    for (Evaluation changed : List.of(
        observe("1".repeat(64), "snapshot#1", STANDARD),
        observe(SEMANTIC, "snapshot#2", STANDARD),
        observe(SEMANTIC, "snapshot#1", "2".repeat(64)))) {
      assertThat(changed.snapshot()).isNotNull();
      var result = DraftPublicationApprovalEvidence.compare(frozen, design(), changed);
      assertThat(result.result()).isEqualTo(Result.STALE);
    }
  }

  @Test
  void providerUnknownOrIncompleteEvidenceCannotBeFrozenOrRevalidated() {
    var frozen = DraftPublicationApprovalEvidence.freeze(design(), valid()).freeze();
    Evaluation unknown = DraftPublicationEvidenceVerifier.evaluate(
        new Request(7L, 701L, "DWD", STRUCTURE,
            List.of(new ColumnRequest("order_id", "BIGINT", 11L))), null);
    assertThat(DraftPublicationApprovalEvidence.freeze(design(), unknown).result())
        .isEqualTo(Result.UNKNOWN);
    assertThat(DraftPublicationApprovalEvidence.freeze(design(), unknown).freeze()).isNull();
    assertThat(DraftPublicationApprovalEvidence.compare(frozen, design(), unknown).result())
        .isEqualTo(Result.UNKNOWN);
    assertThat(DraftPublicationApprovalEvidence.compare(frozen, design(), null).result())
        .isEqualTo(Result.UNKNOWN);
  }

  @Test
  void crossProjectOrModelCannotBorrowAnotherApprovalEvidence() {
    var frozen = DraftPublicationApprovalEvidence.freeze(design(), valid()).freeze();
    var wrongProject = new DesignBinding(8L, 701L, "DWD", STRUCTURE,
        901L, MAPPING, LOGICAL);
    var wrongModel = new DesignBinding(7L, 702L, "DWD", STRUCTURE,
        901L, MAPPING, LOGICAL);
    assertThat(DraftPublicationApprovalEvidence.compare(frozen, wrongProject, valid()).result())
        .isEqualTo(Result.STALE);
    assertThat(DraftPublicationApprovalEvidence.compare(frozen, wrongModel, valid()).result())
        .isEqualTo(Result.STALE);
    assertThat(DraftPublicationApprovalEvidence.freeze(wrongProject, valid()).result())
        .isEqualTo(Result.UNKNOWN);
  }

  @Test
  void incompleteMappingIdentityAndTamperedApprovalDigestFailClosed() {
    var incomplete = new DesignBinding(7L, 701L, "DWD", STRUCTURE, 901L, null, LOGICAL);
    assertThat(DraftPublicationApprovalEvidence.freeze(incomplete, valid()).result())
        .isEqualTo(Result.UNKNOWN);
    var frozen = DraftPublicationApprovalEvidence.freeze(design(), valid()).freeze();
    var tampered = new ApprovalFreeze(frozen.binding(), frozen.standards(),
        frozen.freezeAlgorithm(), "0".repeat(64));
    assertThat(DraftPublicationApprovalEvidence.compare(tampered, design(), valid()).result())
        .isEqualTo(Result.UNKNOWN);
  }

  @Test
  void frozenColumnListCannotBeMutatedOrChangedByProviderAfterFreeze() {
    var request = new ArrayList<>(List.of(
        new ColumnRequest("order_id", "BIGINT", 11L),
        new ColumnRequest("order_status", "VARCHAR", 12L)));
    var facts = new ArrayList<>(List.of(
        new ColumnFact("order_id", "BIGINT", 11L, "VALID", STANDARD,
            "NOT_APPLICABLE", null, null, null),
        new ColumnFact("order_status", "VARCHAR", 12L, "VALID", STANDARD,
            "ENABLED", 3, 3, "f".repeat(64))));
    var proof = DraftPublicationEvidenceVerifier.evaluate(
        new Request(7L, 701L, "DWD", STRUCTURE, request),
        new Observation(7L, 701L, "DWD", STRUCTURE, "VALID",
            DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM, SEMANTIC, "snapshot#1", facts));
    var frozen = DraftPublicationApprovalEvidence.freeze(design(), proof).freeze();
    assertThat(frozen).isNotNull();
    facts.clear();
    request.clear();
    assertThat(frozen.standards().columns()).hasSize(2);
    try {
      frozen.standards().columns().clear();
      throw new AssertionError("frozen snapshot collection must be immutable");
    } catch (UnsupportedOperationException expected) {
      assertThat(frozen.standards().columns()).hasSize(2);
    }
  }
}
