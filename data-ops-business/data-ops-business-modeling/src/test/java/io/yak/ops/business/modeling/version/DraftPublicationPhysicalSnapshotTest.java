package io.yak.ops.business.modeling.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnFact;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Observation;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Verdict;
import io.yak.ops.business.modeling.structure.StructureView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ModelVersion fingerprint parity, without a DB, publish service or user authorization. */
class DraftPublicationPhysicalSnapshotTest {
  private static final String FIELD_SHA = "c".repeat(64);
  private static final String SEMANTIC_SHA = "b".repeat(64);

  private static StructureView.ColumnView column(
      Long rowId, String name, String type, Long fieldId) {
    return new StructureView.ColumnView(rowId, name, type, null, null, false,
        null, null, null, 1, null, null, null, null, null, null,
        fieldId, "METRIC", null, null);
  }

  private static StructureView structure(
      Long id, String status, List<StructureView.ColumnView> columns) {
    return new StructureView(id, "ods_order_detail", "订单明细",
        "MYSQL", status, "订单明细结构", "ods_order_detail", "订单明细",
        columns, List.of("order_id"), List.of(), null, Map.of());
  }

  private static StructureView sample() {
    return structure(701L, "DRAFT", List.of(
        column(100L, "order_id", "BIGINT", 11L),
        column(101L, "order_status", "VARCHAR", 12L)));
  }

  @Test
  void usesExistingModelVersionSemanticFingerprintAndExhaustivePhysicalColumns()
      throws Exception {
    var original = sample();
    var request = DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", original);
    String expected = io.yak.ops.common.version.VersionDigests.sha256Hex(
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
            ModelVersionService.semanticView(original)));
    assertThat(request.physicalStructureSha256()).isEqualTo(expected);
    assertThat(request.projectId()).isEqualTo(7L);
    assertThat(request.modelId()).isEqualTo(701L);
    assertThat(request.columns().stream().map(c -> c.columnName()).toList())
        .containsExactly("order_id", "order_status");
    assertThat(request.columns().stream().map(c -> c.standardFieldId()).toList())
        .containsExactly(11L, 12L);
  }

  @Test
  void rowPrimaryKeysAndVolatileModelStatusCannotFalsifyStructuralDrift() {
    var original = DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", sample());
    var replaced = structure(701L, "PUBLISHED", List.of(
        column(808L, "order_id", "BIGINT", 11L),
        column(809L, "order_status", "VARCHAR", 12L)));
    var result = DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", replaced);
    assertThat(result.physicalStructureSha256()).isEqualTo(original.physicalStructureSha256());
  }

  @Test
  void columnTypeOrStandardBindingChangeCannotReuseStructureFingerprint() {
    var original = DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", sample());
    var typeDrift = structure(701L, "DRAFT", List.of(
        column(100L, "order_id", "DECIMAL", 11L),
        column(101L, "order_status", "VARCHAR", 12L)));
    var standardDrift = structure(701L, "DRAFT", List.of(
        column(100L, "order_id", "BIGINT", 11L),
        column(101L, "order_status", "VARCHAR", 99L)));
    assertThat(DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", typeDrift)
        .physicalStructureSha256()).isNotEqualTo(original.physicalStructureSha256());
    assertThat(DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", standardDrift)
        .physicalStructureSha256()).isNotEqualTo(original.physicalStructureSha256());
  }

  @Test
  void missingStandardFieldRemainsInFullRequestAndFailsClosed() {
    var missing = structure(701L, "DRAFT", List.of(
        column(100L, "order_id", "BIGINT", 11L),
        column(101L, "etl_time", "TIMESTAMP", null)));
    var request = DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", missing);
    assertThat(request.columns()).hasSize(2);
    assertThat(request.columns().get(1).standardFieldId()).isNull();
    assertThat(DraftPublicationEvidenceVerifier.evaluate(request, null).verdict())
        .isEqualTo(Verdict.BLOCKED);
  }

  @Test
  void completeSnapshotAndSemanticFactsMustStillAgreeOnEveryColumn() {
    var request = DraftPublicationPhysicalSnapshot.from(7L, 701L, "DWD", sample());
    var provider = new Observation(7L, 701L, "DWD",
        request.physicalStructureSha256(), "VALID",
        DraftPublicationEvidenceVerifier.PROPOSED_ALGORITHM, SEMANTIC_SHA,
        "review-token-without-consistency-guarantee", List.of(
            new ColumnFact("order_id", "BIGINT", 11L, "VALID", FIELD_SHA,
                "NOT_APPLICABLE", null, null, null),
            new ColumnFact("order_status", "VARCHAR", 12L, "VALID", FIELD_SHA,
                "ENABLED", 3, 3, "d".repeat(64))));
    assertThat(DraftPublicationEvidenceVerifier.evaluateAgainstModelSnapshot(
        request, request, provider).verdict()).isEqualTo(Verdict.VALID_CANDIDATE);
    var omitted = new DraftPublicationEvidenceVerifier.Request(
        request.projectId(), request.modelId(), request.layerCode(),
        request.physicalStructureSha256(), List.of(request.columns().get(0)));
    assertThat(DraftPublicationEvidenceVerifier.evaluateAgainstModelSnapshot(
        omitted, request, provider).verdict()).isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void rejectsForeignStructureEmptyAndDuplicateColumnsBeforeProviderRead() {
    assertThatThrownBy(() -> DraftPublicationPhysicalSnapshot.from(
        7L, 702L, "DWD", sample())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DraftPublicationPhysicalSnapshot.from(
        0L, 701L, "DWD", sample())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DraftPublicationPhysicalSnapshot.from(
        7L, 701L, "ODS", sample())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DraftPublicationPhysicalSnapshot.from(
        7L, 701L, "DWD", structure(701L, "DRAFT", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DraftPublicationPhysicalSnapshot.from(
        7L, 701L, "DWD", structure(701L, "DRAFT", List.of(
            column(100L, "ORDER_ID", "BIGINT", 11L),
            column(101L, "order_id", "BIGINT", 11L)))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
