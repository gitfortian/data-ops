package io.yak.ops.business.metric.domain;

import java.time.LocalDateTime;
import java.util.List;

/** Append-only Definition Validation evidence for one immutable MetricVersion. */
public record MetricValidationEvidence(
    Long evidenceId,
    Long metricId,
    Long metricVersionId,
    int metricVersion,
    ValidationResult result,
    ProviderState providerState,
    List<ValidationIssue> issues,
    String provider,
    String snapshotDigest,
    String checkedBy,
    LocalDateTime checkedAt) {

  public MetricValidationEvidence {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public enum ValidationResult {
    PASSED,
    FAILED,
    NOT_APPLICABLE
  }

  public enum ProviderState {
    READY,
    UNAVAILABLE,
    FORBIDDEN
  }

  public enum Severity {
    BLOCKER,
    WARNING
  }

  public record ValidationIssue(
      String code,
      String field,
      String message,
      Severity severity) {}
}
