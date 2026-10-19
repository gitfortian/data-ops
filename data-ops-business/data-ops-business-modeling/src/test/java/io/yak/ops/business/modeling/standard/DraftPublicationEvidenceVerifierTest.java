package io.yak.ops.business.modeling.standard;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnFact;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnRequest;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Observation;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Request;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Verdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Candidate B2 P01-P18 regression matrix foundation; never invokes a publish
 * service, repository or Runtime and cannot grant an authorization.
 */
class DraftPublicationEvidenceVerifierTest {
  private static final String STRUCTURE_SHA = "a".repeat(64);
  private static final String SEMANTIC_SHA = "b".repeat(64);
  private static final String FIELD_SHA = "c".repeat(64);
  private static final String CODE_SHA = "d".repeat(64);

  private static ColumnRequest orderId() {
    return new ColumnRequest("order_id", "BIGINT", 11L);
  }

  private static ColumnRequest statusColumn() {
    return new ColumnRequest("order_status", "VARCHAR", 12L);
  }

  private static ColumnFact orderEvidence() {
    return new ColumnFact("order_id", "BIGINT", 11L, "VALID", FIELD_SHA,
        "NOT_APPLICABLE", null, null, null);
  }

  private static ColumnFact statusEvidence() {
    return new ColumnFact("order_status", "VARCHAR", 12L, "VALID", FIELD_SHA,
        "ENABLED", 3, 3, CODE_SHA);
  }

  private static Request request(String layerCode) {
    return new Request(7L, 701L, layerCode, STRUCTURE_SHA,
        List.of(orderId(), statusColumn()));
  }

  private static Observation observation(String layerCode, List<ColumnFact> facts) {
    return new Observation(7L, 701L, layerCode, STRUCTURE_SHA, "VALID",
        DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM, SEMANTIC_SHA,
        "semantic-observation-v1", facts);
  }

  private static DraftPublicationEvidenceVerifier.Evaluation verify(
      Request request, Observation observation) {
    return DraftPublicationEvidenceVerifier.evaluate(request, observation);
  }

  @Test
  void proposalAcceptsOnlyCompleteNonOdsSemanticEvidenceWithoutPublishing() {
    for (String layer : List.of("DWD", "DIM", "DWS", "ADS")) {
      var evidence = verify(request(layer),
          observation(layer, List.of(orderEvidence(), statusEvidence())));
      assertThat(evidence.verdict()).isEqualTo(Verdict.VALID_CANDIDATE);
      assertThat(evidence.snapshot()).isNotNull();
      assertThat(evidence.snapshot().modelId()).isEqualTo(701L);
      assertThat(evidence.snapshot().candidateDigestSha256())
          .matches("[a-f0-9]{64}");
      assertThat(evidence.reason()).contains("没有发布授权");
    }
  }

  @Test
  void odsIsOutsideThisBusinessStandardContractAndNotAValidatedSuccess() {
    assertThat(verify(request("ODS"), null).verdict())
        .isEqualTo(Verdict.ODS_OUT_OF_SCOPE);
    assertThat(verify(request("ODS"), null).snapshot()).isNull();
    assertThat(verify(request("UNKNOWN"), null).verdict())
        .isEqualTo(Verdict.UNKNOWN);
    assertThat(verify(request(null), null).verdict())
        .isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void missingStandardFieldEvenForTechnicalColumnIsBlocked() {
    var draft = new Request(7L, 701L, "DWD", STRUCTURE_SHA,
        List.of(orderId(), new ColumnRequest("process_time", "TIMESTAMP", null)));
    var result = verify(draft, observation("DWD", List.of(orderEvidence())));
    assertThat(result.verdict()).isEqualTo(Verdict.BLOCKED);
    assertThat(result.snapshot()).isNull();
  }

  @Test
  void semanticProviderCannotClaimValidWithPartialOrDuplicateColumnSet() {
    var subset = verify(request("DWD"), observation("DWD", List.of(orderEvidence())));
    assertThat(subset.verdict()).isEqualTo(Verdict.UNKNOWN);

    var duplicate = verify(request("DWD"),
        observation("DWD", List.of(orderEvidence(), orderEvidence())));
    assertThat(duplicate.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(duplicate.snapshot()).isNull();
  }

  @Test
  void semanticOwnerBlockedOrUnavailableDoesNotProduceAValidCandidate() {
    var facts = List.of(orderEvidence(), statusEvidence());
    var base = observation("DWD", facts);
    var blocked = new Observation(7L, 701L, "DWD", STRUCTURE_SHA, "BLOCKED",
        base.digestAlgorithmId(), SEMANTIC_SHA, base.consistencyToken(), facts);
    var unavailable = new Observation(7L, 701L, "DWD", STRUCTURE_SHA, "UNKNOWN",
        base.digestAlgorithmId(), SEMANTIC_SHA, base.consistencyToken(), facts);

    assertThat(verify(request("DWD"), blocked).verdict()).isEqualTo(Verdict.BLOCKED);
    assertThat(verify(request("DWD"), unavailable).verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(verify(request("DWD"), null).verdict()).isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void foreignStructureAndWrongLayerEvidenceFailClosed() {
    var good = observation("DWD", List.of(orderEvidence(), statusEvidence()));
    var otherLayer = new Observation(7L, 701L, "DIM", STRUCTURE_SHA, "VALID",
        good.digestAlgorithmId(), SEMANTIC_SHA, good.consistencyToken(), good.columns());
    var changedStructure = new Observation(7L, 701L, "DWD", "e".repeat(64), "VALID",
        good.digestAlgorithmId(), SEMANTIC_SHA, good.consistencyToken(), good.columns());

    assertThat(verify(request("DWD"), otherLayer).verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(verify(request("DWD"), changedStructure).verdict()).isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void mismatchedProjectOrModelProviderEvidenceIsUnknownAndNeverFrozen() {
    Observation valid = observation("DWD", List.of(orderEvidence(), statusEvidence()));
    Observation foreignProject = new Observation(8L, 701L, "DWD", STRUCTURE_SHA,
        "VALID", valid.digestAlgorithmId(), SEMANTIC_SHA, valid.consistencyToken(),
        valid.columns());
    Observation foreignModel = new Observation(7L, 702L, "DWD", STRUCTURE_SHA,
        "VALID", valid.digestAlgorithmId(), SEMANTIC_SHA, valid.consistencyToken(),
        valid.columns());

    assertThat(verify(request("DWD"), foreignProject).verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(verify(request("DWD"), foreignModel).verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(verify(request("DWD"), foreignProject).snapshot()).isNull();
    assertThat(verify(request("DWD"), foreignModel).snapshot()).isNull();

    var validCandidate = verify(request("DWD"), valid).snapshot();
    assertThat(validCandidate.projectId()).isEqualTo(7L);
    assertThat(validCandidate.modelId()).isEqualTo(701L);
    var missingProject = new Request(0L, 701L, "DWD", STRUCTURE_SHA,
        List.of(orderId(), statusColumn()));
    assertThat(verify(missingProject, valid).verdict()).isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void codeSetMixedDisabledOrContradictoryCannotCreateValidEvidence() {
    for (ColumnFact bad : List.of(
        new ColumnFact("order_status", "VARCHAR", 12L, "VALID", FIELD_SHA,
            "MIXED", 3, 2, CODE_SHA),
        new ColumnFact("order_status", "VARCHAR", 12L, "VALID", FIELD_SHA,
            "DISABLED", 3, 0, CODE_SHA),
        new ColumnFact("order_status", "VARCHAR", 12L, "VALID", FIELD_SHA,
            "ENABLED", 3, 2, CODE_SHA),
        new ColumnFact("order_status", "VARCHAR", 12L, "VALID", FIELD_SHA,
            "ENABLED", 3, 3, null))) {
      var result = verify(request("DWD"), observation("DWD",
          List.of(orderEvidence(), bad)));
      assertThat(result.verdict()).isNotEqualTo(Verdict.VALID_CANDIDATE);
      assertThat(result.snapshot()).isNull();
    }
  }

  @Test
  void standardFieldTypeIdentityOrDigestDriftNeverCreatesSnapshot() {
    var wrongIdentity = new ColumnFact("order_id", "BIGINT", 44L, "VALID", FIELD_SHA,
        "NOT_APPLICABLE", null, null, null);
    var wrongType = new ColumnFact("order_id", "DECIMAL", 11L, "VALID", FIELD_SHA,
        "NOT_APPLICABLE", null, null, null);
    var missingDigest = new ColumnFact("order_id", "BIGINT", 11L, "VALID", null,
        "NOT_APPLICABLE", null, null, null);
    for (ColumnFact fact : List.of(wrongIdentity, wrongType, missingDigest)) {
      assertThat(verify(request("DWD"), observation("DWD",
          List.of(fact, statusEvidence()))).verdict()).isEqualTo(Verdict.UNKNOWN);
    }
  }

  @Test
  void incompleteDigestAlgorithmAndConsistencyTokenAreUnknown() {
    var facts = List.of(orderEvidence(), statusEvidence());
    for (Observation malformed : List.of(
        new Observation(7L, 701L, "DWD", STRUCTURE_SHA, "VALID", "LEGACY_UNKNOWN", SEMANTIC_SHA,
            "token", facts),
        new Observation(7L, 701L, "DWD", STRUCTURE_SHA, "VALID",
            DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM, SEMANTIC_SHA, "", facts),
        new Observation(7L, 701L, "DWD", STRUCTURE_SHA, "VALID",
            DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM, null, "token", facts))) {
      assertThat(verify(request("DWD"), malformed).verdict()).isEqualTo(Verdict.UNKNOWN);
    }
  }

  @Test
  void candidateDigestIsIndependentOfProviderOrderButBindsSemanticContentAndToken() {
    var base = observation("DWD", List.of(orderEvidence(), statusEvidence()));
    var result = verify(request("DWD"), base);
    var reversed = new ArrayList<>(base.columns());
    java.util.Collections.reverse(reversed);
    var reorder = verify(request("DWD"), observation("DWD", reversed));
    assertThat(result.snapshot().candidateDigestSha256())
        .isEqualTo(reorder.snapshot().candidateDigestSha256());

    var changedContent = new ColumnFact("order_id", "BIGINT", 11L, "VALID",
        "e".repeat(64), "NOT_APPLICABLE", null, null, null);
    var drift = verify(request("DWD"), observation("DWD",
        List.of(changedContent, statusEvidence())));
    assertThat(drift.snapshot().candidateDigestSha256())
        .isNotEqualTo(result.snapshot().candidateDigestSha256());

    var changedToken = new Observation(7L, 701L, "DWD", STRUCTURE_SHA, "VALID",
        base.digestAlgorithmId(), SEMANTIC_SHA, "token-after-change", base.columns());
    assertThat(verify(request("DWD"), changedToken).snapshot().candidateDigestSha256())
        .isNotEqualTo(result.snapshot().candidateDigestSha256());
  }

  @Test
  void invalidOrDuplicateTargetColumnsCannotBeTreatedAsAFullSet() {
    var duplicate = new Request(7L, 701L, "DWD", STRUCTURE_SHA,
        List.of(orderId(), new ColumnRequest("ORDER_ID", "BIGINT", 11L)));
    assertThat(verify(duplicate, null).verdict()).isEqualTo(Verdict.UNKNOWN);
    var empty = new Request(7L, 701L, "DWD", STRUCTURE_SHA, List.of());
    assertThat(verify(empty, null).verdict()).isEqualTo(Verdict.UNKNOWN);
  }
}
