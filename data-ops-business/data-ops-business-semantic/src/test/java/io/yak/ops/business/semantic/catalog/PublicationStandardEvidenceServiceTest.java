package io.yak.ops.business.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.BatchRequest;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.ColumnRequest;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.Verdict;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicationStandardEvidenceServiceTest {
  private static final String STRUCTURE = "a".repeat(64);
  private CurrentProject project;
  private SemanticPublicationRevisionService revision;
  private SemanticFieldRepository fields;
  private StandardQueryApi standards;
  private PublicationStandardEvidenceService service;

  @BeforeEach
  void init() {
    project = mock(CurrentProject.class);
    revision = mock(SemanticPublicationRevisionService.class);
    fields = mock(SemanticFieldRepository.class);
    standards = mock(StandardQueryApi.class);
    when(project.requireProjectId()).thenReturn(7L);
    when(revision.lockExistingForReview()).thenReturn(41L);
    service = new PublicationStandardEvidenceService(project, revision, fields, standards);
  }

  private static BatchRequest request(ColumnRequest... columns) {
    return new BatchRequest(7L, 701L, "DWD", STRUCTURE, List.of(columns));
  }

  private static ColumnRequest order(String name, Long id) {
    return new ColumnRequest(name, "BIGINT", id, "DIMENSION");
  }

  private static Standard.KindFields typeDefinition() {
    return new Standard.KindFields(null, null, null, null, "BIGINT", "BIGINT",
        null, null, null, null, null, null, null, null, null, null, null);
  }

  private static Standard type() {
    return new Standard(21L, StandardKind.TYPE, "TYPE_INT", "整数", StandardStatus.ENABLED,
        3, 0, false, null, typeDefinition(), "test", null, null);
  }

  private static StandardField field(Long id, String role, String codeSet, Long security) {
    return new StandardField(id, "order_id", "订单ID", role, "ENABLED", "BIGINT",
        21L, null, null, codeSet, security, null, "MANUAL", 5, false,
        "test", null, null);
  }

  private void validField(Long id, String role, String codeSet, Long security) {
    when(fields.findById(id)).thenReturn(Optional.of(field(id, role, codeSet, security)));
    when(standards.get(21L)).thenReturn(type());
  }

  @Test
  void realCurrentFactsProduceRevisionBoundFullColumnEvidence() {
    validField(11L, "DIMENSION", null, null);
    validField(12L, "DIMENSION", null, null);
    var result = service.evaluate(request(order("order_id", 11L),
        order("order_status", 12L)));
    assertThat(result.verdict()).isEqualTo(Verdict.VALID);
    assertThat(result.semanticRevision()).isEqualTo(41L);
    assertThat(result.evidenceSha256()).matches("[a-f0-9]{64}");
    assertThat(result.columns()).hasSize(2);
    assertThat(result.columns().get(0).applicableStandards()).hasSize(1);
    verify(revision).lockExistingForReview();
  }

  @Test
  void projectMismatchStopsBeforeAnyFactReadOrRevisionLock() {
    var other = new BatchRequest(8L, 701L, "DWD", STRUCTURE,
        List.of(order("order_id", 11L)));
    assertThat(service.evaluate(other).verdict()).isEqualTo(Verdict.UNKNOWN);
    verify(revision, never()).lockExistingForReview();
    verify(fields, never()).findById(11L);
  }

  @Test
  void fullColumnRequestCannotSuppressMissingTechnicalStandard() {
    validField(11L, "DIMENSION", null, null);
    var result = service.evaluate(request(order("order_id", 11L),
        order("etl_time", null)));
    assertThat(result.verdict()).isEqualTo(Verdict.BLOCKED);
    assertThat(result.columns()).hasSize(2);
    assertThat(result.safeReasonCodes()).contains("MISSING_STANDARD");
  }

  @Test
  void duplicatePhysicalColumnNamesOrInvalidStructureCannotYieldValid() {
    var result = service.evaluate(request(order("order_id", 11L),
        order("ORDER_ID", 11L)));
    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    verify(revision, never()).lockExistingForReview();
  }

  @Test
  void partiallyDisabledCodeSetBlocksEntireDimensionColumn() {
    validField(11L, "DIMENSION", "order_status", null);
    when(standards.codeSetReference("order_status"))
        .thenReturn(new StandardQueryApi.CodeSetReference(
            "order_status", 3, 2, "MIXED", "e".repeat(64)));
    var result = service.evaluate(request(order("status", 11L)));
    assertThat(result.verdict()).isEqualTo(Verdict.BLOCKED);
    assertThat(result.safeReasonCodes()).contains("INVALID_CODE_SET");
  }

  @Test
  void fullCodeSetDigestChangesFrozenEvidenceEvenAtSameRevision() {
    validField(11L, "DIMENSION", "order_status", null);
    when(standards.codeSetReference("order_status"))
        .thenReturn(new StandardQueryApi.CodeSetReference(
            "order_status", 3, 3, "ENABLED", "c".repeat(64)),
            new StandardQueryApi.CodeSetReference(
                "order_status", 3, 3, "ENABLED", "d".repeat(64)));
    var first = service.evaluate(request(order("status", 11L)));
    var second = service.evaluate(request(order("status", 11L)));
    assertThat(first.verdict()).isEqualTo(Verdict.VALID);
    assertThat(second.verdict()).isEqualTo(Verdict.VALID);
    assertThat(first.evidenceSha256()).isNotEqualTo(second.evidenceSha256());
  }

  @Test
  void securityBoundFieldRequiresSeparateTrustedAuthorizationEvidence() {
    validField(11L, "DIMENSION", null, 88L);
    when(standards.get(88L)).thenReturn(new Standard(88L, StandardKind.SECURITY,
        "SEC_L2", "秘密", StandardStatus.ENABLED, 2, 0, false, null,
        new Standard.KindFields(null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, "L2", "REDACT"),
        "test", null, null));
    var result = service.evaluate(request(order("order_id", 11L)));
    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.evidenceSha256()).isNull();
    assertThat(result.safeReasonCodes()).contains("SECURITY_EPOCH_UNAVAILABLE");
    assertThat(result.toString()).doesNotContain("REDACT");
  }

  @Test
  void revisionReadFailureFailsClosedNotValid() {
    when(revision.lockExistingForReview()).thenThrow(new IllegalStateException("db unavailable"));
    var result = service.evaluate(request(order("order_id", 11L)));
    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.evidenceSha256()).isNull();
    assertThat(result.safeReasonCodes()).containsExactly("PROVIDER_UNAVAILABLE");
  }

  @Test
  void physicalTypeMismatchIsBlockedBeforeRelease() {
    validField(11L, "DIMENSION", null, null);
    var result = service.evaluate(request(
        new ColumnRequest("amount", "DECIMAL", 11L, "DIMENSION")));
    assertThat(result.verdict()).isEqualTo(Verdict.BLOCKED);
    assertThat(result.safeReasonCodes()).contains("PHYSICAL_TYPE_MISMATCH");
  }
}
