package io.yak.ops.business.metric.publication;

import java.util.List;

/**
 * Publication prerequisite contract.
 *
 * <p>Each gate evaluates an immutable MetricVersion subject and returns explicit evidence.
 * Publication orchestration must fail closed when a required provider is unavailable/forbidden;
 * NOT_APPLICABLE is a first-class truthful outcome for validation capabilities that do not exist
 * for this product/version. MetricStatus.ENABLED is never publication approval.
 */
public interface MetricPublicationGate {

  String provider();

  GateEvidence evaluate(PublicationSubject subject);

  enum GateStatus {
    READY,
    BLOCKED,
    UNAVAILABLE,
    FORBIDDEN,
    NOT_APPLICABLE
  }

  record PublicationSubject(
      Long metricId,
      Long metricVersionId,
      int metricVersion,
      String snapshotDigest) {
  }

  record GateEvidence(
      String provider,
      GateStatus status,
      String evidenceRef,
      List<String> issues) {

    public GateEvidence {
      issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static GateEvidence ready(String provider, String evidenceRef) {
      return new GateEvidence(provider, GateStatus.READY, evidenceRef, List.of());
    }

    public static GateEvidence blocked(String provider, String evidenceRef, List<String> issues) {
      return new GateEvidence(provider, GateStatus.BLOCKED, evidenceRef, issues);
    }

    public static GateEvidence unavailable(String provider, String reason) {
      return failure(provider, GateStatus.UNAVAILABLE, reason);
    }

    public static GateEvidence forbidden(String provider, String reason) {
      return failure(provider, GateStatus.FORBIDDEN, reason);
    }

    public static GateEvidence notApplicable(String provider, String reason) {
      return failure(provider, GateStatus.NOT_APPLICABLE, reason);
    }

    private static GateEvidence failure(String provider, GateStatus status, String reason) {
      return new GateEvidence(
          provider,
          status,
          null,
          reason == null || reason.isBlank() ? List.of() : List.of(reason));
    }
  }
}
