package io.yak.ops.business.metric.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.publication.MetricPublicationGate.GateStatus;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationResult;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ProviderState;
import io.yak.ops.business.metric.validation.MetricDefinitionValidationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricDefinitionValidationPublicationGateTest {

  private final MetricDefinitionValidationService validationService =
      mock(MetricDefinitionValidationService.class);
  private final MetricDefinitionValidationPublicationGate gate =
      new MetricDefinitionValidationPublicationGate(validationService);

  @Test
  void blocksWhenNoReadyDefinitionEvidenceExists() {
    PublicationSubject subject = subject();
    when(validationService.history(7L, 3)).thenReturn(List.of());

    var evidence = gate.evaluate(subject);

    assertThat(evidence.status()).isEqualTo(GateStatus.BLOCKED);
    assertThat(evidence.issues()).containsExactly("DEFINITION_VALIDATION_PASSED_EVIDENCE_REQUIRED");
  }

  @Test
  void ignoresReadyEvidenceFromAnotherProvider() {
    PublicationSubject subject = subject();
    when(validationService.history(7L, 3)).thenReturn(List.of(
        readyEvidence(91L, 31L, "sha-3", "another-validator/v1")));

    var evidence = gate.evaluate(subject);

    assertThat(evidence.status()).isEqualTo(GateStatus.BLOCKED);
    assertThat(evidence.issues()).containsExactly("DEFINITION_VALIDATION_PASSED_EVIDENCE_REQUIRED");
  }

  @Test
  void blocksWhenReadyEvidenceDoesNotMatchImmutableSubject() {
    PublicationSubject subject = subject();
    when(validationService.history(7L, 3)).thenReturn(List.of(
        readyEvidence(91L, 99L, "other-digest", MetricDefinitionValidationService.PROVIDER)));

    var evidence = gate.evaluate(subject);

    assertThat(evidence.status()).isEqualTo(GateStatus.BLOCKED);
    assertThat(evidence.evidenceRef()).isEqualTo("metric-validation-evidence:91");
    assertThat(evidence.issues()).containsExactly("DEFINITION_VALIDATION_EVIDENCE_SUBJECT_MISMATCH");
  }

  @Test
  void becomesReadyOnlyForExactVersionAndSnapshotEvidence() {
    PublicationSubject subject = subject();
    when(validationService.history(7L, 3)).thenReturn(List.of(
        readyEvidence(91L, 31L, "sha-3", MetricDefinitionValidationService.PROVIDER)));

    var evidence = gate.evaluate(subject);

    assertThat(evidence.status()).isEqualTo(GateStatus.READY);
    assertThat(evidence.evidenceRef()).isEqualTo("metric-validation-evidence:91");
    assertThat(evidence.issues()).isEmpty();
  }

  private static PublicationSubject subject() {
    return new PublicationSubject(7L, 31L, 3, "sha-3");
  }

  private static MetricValidationEvidence readyEvidence(
      Long evidenceId, Long versionId, String digest, String provider) {
    return new MetricValidationEvidence(
        evidenceId,
        7L,
        versionId,
        3,
        ValidationResult.PASSED,
        ProviderState.READY,
        List.of(),
        provider,
        digest,
        "tester",
        LocalDateTime.of(2026, 9, 27, 12, 0));
  }
}
