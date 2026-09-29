package io.yak.ops.business.metric.publication;

import io.yak.ops.business.metric.publication.MetricPublicationGate.GateEvidence;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationResult;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ProviderState;
import io.yak.ops.business.metric.validation.MetricDefinitionValidationService;
import java.util.List;
import java.util.Objects;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Publication gate backed by append-only Definition Validation evidence.
 *
 * <p>The gate never runs validation as a side effect. Publication readiness may only consume an
 * already-recorded PASSED result from a READY provider for the exact immutable MetricVersion
 * subject and snapshot digest. This keeps validation evidence independently auditable and prevents a stale row
 * from authorizing a different snapshot.
 */
@Component
@Order(10)
public class MetricDefinitionValidationPublicationGate implements MetricPublicationGate {

  public static final String PROVIDER = "metric-definition-validation-gate/v1";

  private final MetricDefinitionValidationService validationService;

  public MetricDefinitionValidationPublicationGate(
      MetricDefinitionValidationService validationService) {
    this.validationService = validationService;
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public GateEvidence evaluate(PublicationSubject subject) {
    List<MetricValidationEvidence> history = validationService.history(
        subject.metricId(), subject.metricVersion());

    MetricValidationEvidence evidence = history.stream()
        .filter(item -> item.result() == ValidationResult.PASSED)
        .filter(item -> item.providerState() == ProviderState.READY)
        .filter(item -> MetricDefinitionValidationService.PROVIDER.equals(item.provider()))
        .findFirst()
        .orElse(null);

    if (evidence == null) {
      return GateEvidence.blocked(
          PROVIDER,
          null,
          List.of("DEFINITION_VALIDATION_PASSED_EVIDENCE_REQUIRED"));
    }
    if (evidence.evidenceId() == null
        || !Objects.equals(evidence.metricVersionId(), subject.metricVersionId())
        || !Objects.equals(evidence.snapshotDigest(), subject.snapshotDigest())) {
      return GateEvidence.blocked(
          PROVIDER,
          evidence.evidenceId() == null ? null : evidenceRef(evidence.evidenceId()),
          List.of("DEFINITION_VALIDATION_EVIDENCE_SUBJECT_MISMATCH"));
    }

    return GateEvidence.ready(PROVIDER, evidenceRef(evidence.evidenceId()));
  }

  private static String evidenceRef(Long evidenceId) {
    return "metric-validation-evidence:" + evidenceId;
  }
}
