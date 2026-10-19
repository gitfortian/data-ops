package io.yak.ops.business.semantic.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.BatchRequest;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.BatchResult;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.ColumnEvidence;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.ColumnRequest;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.StandardReference;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.Verdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R1 T1 typed SPI contract tests; no production provider or authority is registered. */
class PublicationStandardEvidenceApiTest {
  private static final String SHA = "a".repeat(64);

  @Test
  void requestAndResponseDetachPublisherOwnedMutableEvidenceLists() {
    var submitted = new ArrayList<>(List.of(
        new ColumnRequest("order_id", "BIGINT", 11L, "DIMENSION")));
    var request = new BatchRequest(7L, 701L, "DWD", SHA, submitted);
    submitted.clear();
    assertThat(request.columns()).hasSize(1);
    assertThatThrownBy(() -> request.columns().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    var standards = new ArrayList<>(List.of(
        new StandardReference("TYPE", 21L, 3, "ENABLED", "b".repeat(64))));
    var column = new ColumnEvidence("order_id", "BIGINT", 11L, "DIMENSION",
        "VALID", null, standards, null, null);
    standards.clear();
    assertThat(column.applicableStandards()).hasSize(1);
    assertThatThrownBy(() -> column.applicableStandards().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    var fields = new ArrayList<>(List.of(column));
    var findings = new ArrayList<>(List.of("PROVIDER_UNKNOWN"));
    var response = new BatchResult(Verdict.UNKNOWN, 7L, 701L, "DWD", SHA,
        null, null, null, fields, findings);
    fields.clear();
    findings.clear();
    assertThat(response.columns()).hasSize(1);
    assertThat(response.safeReasonCodes()).containsExactly("PROVIDER_UNKNOWN");
  }

  @Test
  void unknownIsAFirstClassResultWithoutFabricatedRevisionOrDigest() {
    var result = new BatchResult(Verdict.UNKNOWN, 7L, 701L, "DWD",
        SHA, null, null, null, List.of(), List.of("AUTH_CONTEXT_UNAVAILABLE"));
    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.semanticRevision()).isNull();
    assertThat(result.evidenceSha256()).isNull();
    assertThat(result.safeReasonCodes()).containsExactly("AUTH_CONTEXT_UNAVAILABLE");
  }

  @Test
  void validCannotBeForgedWithoutRevisionOrDigestOrPerColumnValidation() {
    var validField = new ColumnEvidence("order_id", "BIGINT", 11L, "DIMENSION",
        "VALID", null, List.of(), null, null);
    var invalidField = new ColumnEvidence("order_id", "BIGINT", 11L, "DIMENSION",
        "NEEDS_REVIEW", null, List.of(), null, "ROLE_INCOMPATIBLE");
    assertThatThrownBy(() -> new BatchResult(Verdict.VALID, 7L, 701L, "DWD",
        SHA, null, "B2_SCHEMA_V1", SHA, List.of(validField), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BatchResult(Verdict.VALID, 7L, 701L, "DWD",
        SHA, 1L, "B2_SCHEMA_V1", null, List.of(validField), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BatchResult(Verdict.VALID, 7L, 701L, "DWD",
        SHA, 1L, "B2_SCHEMA_V1", SHA, List.of(invalidField), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BatchResult(Verdict.VALID, 7L, 701L, "DWD",
        SHA, 1L, "B2_SCHEMA_V1", SHA, List.of(validField), List.of("PROVIDER_UNKNOWN")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new BatchResult(Verdict.VALID, 7L, 701L, "DWD",
        SHA, 1L, "B2_SCHEMA_V1", SHA, List.of(validField), List.of()).verdict())
        .isEqualTo(Verdict.VALID);
  }

  @Test
  void incompleteCollectionsCannotMasqueradeAsWellFormedSnapshot() {
    assertThatThrownBy(() -> new BatchRequest(7L, 701L, "DWD", SHA, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ColumnEvidence("order_id", "BIGINT", 11L,
        "DIMENSION", "VALID", null, null, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BatchResult(Verdict.VALID, 7L, 701L, "DWD",
        SHA, 1L, "ALGO", SHA, null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }
}
